/*
    Copyright 2011 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith, 2014.

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

#ifndef __dwi_tractography_mapping_mapper_plugins_h__
#define __dwi_tractography_mapping_mapper_plugins_h__


#include <vector>

#include "point.h"
#include "image/buffer_preload.h"
#include "image/voxel.h"
#include "image/interp/linear.h"
#include "math/SH.h"
#include "math/vector.h"

#include "dwi/directions/set.h"

#include "dwi/tractography/mapping/twi_stats.h"


namespace MR {
namespace DWI {
namespace Tractography {
namespace Mapping {





class DixelMappingPlugin
{
  public:
    DixelMappingPlugin (const DWI::Directions::FastLookupSet& directions) :
        dirs (directions) { }
    DixelMappingPlugin (const DixelMappingPlugin& that) :
        dirs (that.dirs) { }
    size_t operator() (const Point<float>& d) const { return dirs.select_direction (d); }
  private:
    const DWI::Directions::FastLookupSet& dirs;
};



class TODMappingPlugin
{
  public:
    TODMappingPlugin (const size_t N) :
        generator (new Math::SH::aPSF<float> (Math::SH::LforN (N))) { }
    TODMappingPlugin (const TODMappingPlugin& that) :
        generator (that.generator) { }
    void operator() (Math::Vector<float>& sh, const Point<float>& d) const { (*generator) (sh, d); }
  private:
    std::shared_ptr< Math::SH::aPSF<float> > generator;
};





class TWIImagePluginBase
{

    typedef Image::BufferPreload<float>::voxel_type input_voxel_type;

  public:
    TWIImagePluginBase (const std::string& input_image) :
        data (new Image::BufferPreload<float> (input_image)),
        voxel (*data),
        interp (voxel) { }

    TWIImagePluginBase (const TWIImagePluginBase& that) :
        data (that.data),
        voxel (that.voxel),
        interp (voxel) { }

    virtual ~TWIImagePluginBase() { }


    virtual void load_factors (const std::vector< Point<float> >&, std::vector<float>&) const = 0;


  protected:
    std::shared_ptr< Image::BufferPreload<float> > data;
    const input_voxel_type voxel;
    // Each instance of the class has its own interpolator for obtaining values
    //   in a thread-safe fashion
    mutable Image::Interp::Linear< input_voxel_type > interp;

    // New helper function; find the last point on the streamline from which valid image information can be read
    const Point<float> get_last_point_in_fov (const std::vector< Point<float> >&, const bool) const;

};





class TWIScalarImagePlugin : public TWIImagePluginBase
{
  public:
    TWIScalarImagePlugin (const std::string& input_image, const tck_stat_t track_statistic) :
        TWIImagePluginBase (input_image),
        statistic (track_statistic)
    {
      if (!((data->ndim() == 3) || (data->ndim() == 4 && data->dim(3) == 1)))
        throw Exception ("Scalar image used for TWI must be a 3D image");
      if (data->ndim() == 4)
        interp[3] = 0;
    }

    TWIScalarImagePlugin (const TWIScalarImagePlugin& that) :
        TWIImagePluginBase (that),
        statistic (that.statistic)
    {
      if (data->ndim() == 4)
        interp[3] = 0;
    }

    ~TWIScalarImagePlugin() { }


    void load_factors (const std::vector< Point<float> >&, std::vector<float>&) const;

  private:
    const tck_stat_t statistic;

};





class TWIFODImagePlugin : public TWIImagePluginBase
{
  public:
    TWIFODImagePlugin (const std::string& input_image) :
        TWIImagePluginBase (input_image),
        N (data->dim(3)),
        sh_coeffs (new float[N]),
        precomputer (new Math::SH::PrecomputedAL<float> ())
    {
      Math::SH::check (*data);
      precomputer->init (Math::SH::LforN (N));
    }

    TWIFODImagePlugin (const TWIFODImagePlugin& that) :
        TWIImagePluginBase (that),
        N (that.N),
        sh_coeffs (new float[N]),
        precomputer (that.precomputer) { }

    ~TWIFODImagePlugin()
    {
      delete[] sh_coeffs;
      sh_coeffs = NULL;
    }


    void load_factors (const std::vector< Point<float> >&, std::vector<float>&) const;


  private:
    const size_t N;
    float* sh_coeffs;
    std::shared_ptr< Math::SH::PrecomputedAL<float> > precomputer;

};








}
}
}
}

#endif



