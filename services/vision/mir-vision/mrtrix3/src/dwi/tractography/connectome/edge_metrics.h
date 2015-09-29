/*
    Copyright 2012 Brain Research Institute, Melbourne, Australia

    Written by Robert Smith, 2012.

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



#ifndef __dwi_tractography_connectome_edge_metrics_h__
#define __dwi_tractography_connectome_edge_metrics_h__


#include <vector>

#include "point.h"

#include "image/buffer.h"
#include "image/loop.h"
#include "image/voxel.h"
#include "image/interp/linear.h"

#include "connectome/connectome.h"

#include "dwi/tractography/streamline.h"




namespace MR {
namespace DWI {
namespace Tractography {
namespace Connectome {



// Provide a common interface for performing these calculations
class Metric_base {

  public:
    Metric_base (const bool scale) : scale_by_count (scale) { }
    virtual ~Metric_base() { }

    virtual double operator() (const Streamline<>& tck, const NodePair& nodes) const
    {
      throw Exception ("Calling empty virtual function Metric_base::operator()");
    }

    virtual double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const
    {
      throw Exception ("Calling empty virtual function Metric_base::operator()");
    }

    const bool scale_edges_by_streamline_count() const { return scale_by_count; }

  private:
    const bool scale_by_count;

};



class Metric_count : public Metric_base {

  public:
    Metric_count () : Metric_base (false) { }

    double operator() (const Streamline<>& tck, const NodePair& nodes) const override
    {
      return 1.0;
    }

    double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const override
    {
      return 1.0;
    }

};


class Metric_meanlength : public Metric_base {

  public:
    Metric_meanlength () : Metric_base (true) { }

    double operator() (const Streamline<>& tck, const NodePair& nodes) const override
    {
      return tck.calc_length();
    }

    double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const override
    {
      return tck.calc_length();
    }

};


class Metric_invlength : public Metric_base {

  public:
    Metric_invlength () : Metric_base (false) { }

    double operator() (const Streamline<>& tck, const NodePair& nodes) const override
    {
      return (tck.size() > 1 ? (1.0 / tck.calc_length()) : 0);
    }

    double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const override
    {
      return (tck.size() > 1 ? (1.0 / tck.calc_length()) : 0);
    }

};



class Metric_invnodevolume : public Metric_base {

  public:
    Metric_invnodevolume (Image::Buffer<node_t>& in_data) :
      Metric_base (false)
    {
      auto in = in_data.voxel();
      Image::Loop loop;
      for (auto l = Image::Loop() (in); l; ++l) {
        const node_t node_index = in.value();
        if (node_index >= node_volumes.size())
          node_volumes.resize (node_index + 1, 0);
        node_volumes[node_index]++;
      }
    }

    virtual double operator() (const Streamline<>& tck, const NodePair& nodes) const override
    {
      const size_t volume_sum = node_volumes[nodes.first] + node_volumes[nodes.second];
      if (!volume_sum) return 0.0;
      return (2.0 / double(volume_sum));
    }

    virtual double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const override
    {
      size_t volume_sum = 0;
      for (std::vector<node_t>::const_iterator n = nodes.begin(); n != nodes.end(); ++n)
        volume_sum += node_volumes[*n];
      if (!volume_sum) return 0.0;
      return (nodes.size() / double(volume_sum));
    }

  private:
    std::vector<size_t> node_volumes;

};



class Metric_invlength_invnodevolume : public Metric_invnodevolume {

  public:
    Metric_invlength_invnodevolume (Image::Buffer<node_t>& in_data) :
      Metric_invnodevolume (in_data) { }

    double operator() (const Streamline<>& tck, const NodePair& nodes) const override
    {
      return (tck.size() > 1 ? (Metric_invnodevolume::operator() (tck, nodes) / tck.calc_length()) : 0);
    }

    double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const override
    {
      return (tck.size() > 1 ? (Metric_invnodevolume::operator() (tck, nodes) / tck.calc_length()) : 0);
    }

};



class Metric_meanscalar : public Metric_base {

  public:
    Metric_meanscalar (const std::string& path) :
      Metric_base (true),
      image (path),
      v (image),
      interp_template (v) { }

    double operator() (const Streamline<>& tck, const NodePair& nodes) const override
    {
      auto interp = interp_template;
      double sum = 0.0;
      size_t count = 0.0;
      for (Streamline<>::const_iterator i = tck.begin(); i != tck.end(); ++i) {
        if (!interp.scanner (*i)) {
          sum += interp.value();
          ++count;
        }
      }
      return (count ? (sum / double(count)) : 0.0);
    }

    double operator() (const Streamline<>& tck, const std::vector<node_t>& nodes) const override
    {
      return (*this) (tck, std::make_pair (0, 0));
    }


  private:
    Image::Buffer<float> image;
    const Image::Buffer<float>::voxel_type v;
    const Image::Interp::Linear< Image::Buffer<float>::voxel_type > interp_template;

};







}
}
}
}


#endif

