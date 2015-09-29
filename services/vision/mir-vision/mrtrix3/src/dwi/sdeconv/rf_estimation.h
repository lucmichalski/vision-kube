/*
    Copyright 2008 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith, 2013.

    This file is part of MRtrix.

    MRtrix is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MRtrix is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with MRtrix.  If not, see <http://www.gnu.org/licenses/>.

*/



#include <limits>
#include <vector>

#include "exception.h"
#include "point.h"
#include "memory.h"

#include "file/ofstream.h"

#include "image/buffer.h"
#include "image/buffer_preload.h"
#include "image/buffer_scratch.h"
#include "image/iterator.h"
#include "image/nav.h"
#include "image/position.h"
#include "image/value.h"

#include "math/matrix.h"
#include "math/rng.h"
#include "math/SH.h"
#include "math/vector.h"

#include "thread.h"

#include "dwi/fmls.h"
#include "dwi/gradient.h"
#include "dwi/sdeconv/constrained.h"



namespace MR {
namespace DWI {
namespace RF {



class SFThresholds;
class FODSegResult;



class SFThresholds
{
  public:
    SFThresholds (const float volume_ratio) :
        volume_ratio   (volume_ratio),
        min_integral   (0.0),
        max_integral   (std::numeric_limits<float>::max()),
        max_dispersion (std::numeric_limits<float>::max()) { }

    float get_volume_ratio  () const { return volume_ratio; }
    float get_min_integral  () const { return min_integral; }
    float get_max_integral  () const { return max_integral; }
    float get_max_dispersion() const { return max_dispersion; }

    void update (const std::vector<FODSegResult>&, const float, const float, const size_t);

  private:
    float volume_ratio, min_integral, max_integral, max_dispersion;
};





class FODSegResult
{
  public:
    FODSegResult (const DWI::FMLS::FOD_lobes& lobes) :
        vox (lobes.vox),
        peak_dir (lobes[0].get_peak_dir()),
        integral (lobes[0].get_integral()),
        dispersion (integral / lobes[0].get_peak_value())
    {
      assert (lobes.size());
      float sum_integrals = 0.0;
      for (size_t i = 1; i != lobes.size(); ++i)
        sum_integrals += lobes[i].get_integral();
      volume_ratio = sum_integrals / lobes[0].get_integral();
    }

    FODSegResult() : vox (), peak_dir (), integral (NAN), dispersion (NAN), volume_ratio (NAN) { }

    bool is_sf (const SFThresholds& thresholds) const;

    const Point<int>&   get_vox()      const { return vox; }
    const Point<float>& get_peak_dir() const { return peak_dir; }

    float get_integral()     const { return integral; }
    float get_dispersion()   const { return dispersion; }
    float get_volume_ratio() const { return volume_ratio; }


  private:
    Point<int> vox;
    Point<float> peak_dir;
    float integral, dispersion, volume_ratio;

};





class FODCalcAndSeg
{
  public:
    FODCalcAndSeg (Image::BufferPreload<float>& dwi,
               Image::BufferScratch<bool>& mask,
               const DWI::CSDeconv<float>::Shared& csd_shared,
               const DWI::Directions::Set& dirs,
               const size_t lmax,
               std::vector<FODSegResult>& output) :
        in (dwi),
        mask (mask),
        csd (csd_shared),
        fmls (new DWI::FMLS::Segmenter (dirs, lmax)),
        lmax (lmax),
        output (output),
        mutex (new std::mutex)
    {
      // TODO Still unhappy with the segmentation of small FOD lobes in this context
      // One possibility would be to NOT throw out negative lobes, use no thresholds, and instead
      //   quantify the volume ratio using the sum of both positive and negative lobes
      //   other than the dominant lobe
      //fmls->set_ratio_to_negative_lobe_integral (0.0);
      //fmls->set_ratio_to_negative_lobe_mean_peak (0.0);
      //fmls->set_peak_value_threshold (0.0);
      fmls->set_ratio_of_peak_value_to_merge (1.0); // NEVER merge lobes with distinct peaks
      fmls->set_create_null_lobe (false);
      fmls->set_create_lookup_table (false); // No need for it
    }


    FODCalcAndSeg (const FODCalcAndSeg& that) :
        in         (that.in),
        mask       (that.mask),
        csd        (that.csd),
        fmls       (that.fmls),
        lmax       (that.lmax),
        output     (that.output),
        mutex      (that.mutex) { }

    ~FODCalcAndSeg() { }


    bool operator() (const Image::Iterator& pos);


  private:
    Image::BufferPreload<float>::voxel_type in;
    Image::BufferScratch<bool>::voxel_type mask;
    DWI::CSDeconv<float> csd;
    std::shared_ptr<DWI::FMLS::Segmenter> fmls;
    const size_t lmax;
    std::vector<FODSegResult>& output;

    std::shared_ptr<std::mutex> mutex;

};




class SFSelector
{

  public:
    SFSelector (const std::vector<FODSegResult>& results,
                const SFThresholds& thresholds,
                Image::BufferScratch<bool>& output_mask) :
        input (results),
        thresholds (thresholds),
        it (input.begin()),
        output (output_mask) { }

    SFSelector (const SFSelector& that) :
        input (that.input),
        thresholds (that.thresholds),
        it (that.it),
        output (that.output)
    {
      throw Exception ("Do not instantiate copy constructor of SFSelector class!");
    }

    bool operator() (FODSegResult& out);

  private:
    const std::vector<FODSegResult>& input;
    const SFThresholds& thresholds;

    std::vector<FODSegResult>::const_iterator it;
    Image::BufferScratch<bool>::voxel_type output;

};






// TODO If implementing the (bias-corrected) gradient descent version of response function determination,
//   this may exist as an alternative Response class that stores all individual voxel SH data using the += operator,
//   and performs the final estimation in the result() function; ResponseEstimator then becomes a templated class
class Response
{
  public:
    Response (const size_t lmax) :
        data (lmax/2+1),
        count (0)
    {
      data.zero();
    }

    Response& operator+= (const Math::Vector<float>& i)
    {
      assert (i.size() == data.size());
      data += i;
      ++count;
      return *this;
    }

    Math::Vector<float> result() const
    {
      assert (count);
      Math::Vector<float> result (data);
      result /= float(count);
      return result;
    }

    size_t get_count() const { return count; }

  private:
    Math::Vector<double> data;
    size_t count;
};






class ResponseEstimator
{

  public:
    ResponseEstimator (Image::BufferPreload<float>& dwi_data,
                       const DWI::CSDeconv<float>::Shared& csd_shared,
                       const size_t lmax,
                       Response& output) :
        dwi (dwi_data),
        shared (csd_shared),
        lmax (lmax),
        output (output),
        mutex (new std::mutex()) { }

    ResponseEstimator (const ResponseEstimator& that) :
        dwi (that.dwi),
        shared (that.shared),
        lmax (that.lmax),
        output (that.output),
        rng (that.rng),
        mutex (that.mutex) { }


    bool operator() (const FODSegResult&);


  private:
    Image::BufferPreload<float>::voxel_type dwi;
    const DWI::CSDeconv<float>::Shared& shared;
    const size_t lmax;
    Response& output;

    mutable Math::RNG::Uniform<float> rng;

    std::shared_ptr<std::mutex> mutex;

    Math::Matrix<float> gen_rotation_matrix (const Point<float>&) const;

};




}
}
}


