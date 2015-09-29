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



#ifndef __dwi_tractography_connectome_tck2nodes_h__
#define __dwi_tractography_connectome_tck2nodes_h__


#include <stdint.h>
#include <deque>
#include <vector>
#include <map>
#include <set>

#include "point.h"

#include "image/buffer.h"
#include "image/buffer_preload.h"
#include "image/nav.h"
#include "image/transform.h"
#include "image/voxel.h"
#include "image/interp/linear.h"

#include "dwi/tractography/connectome/connectome.h"

#include "dwi/tractography/streamline.h"


namespace MR {
namespace DWI {
namespace Tractography {
namespace Connectome {




// Provides a common interface for assigning a streamline to the relevant parcellation node pair
// Note that this class is NOT copy-constructed, so derivative classes must be thread-safe
class Tck2nodes_base {

  public:
    Tck2nodes_base (Image::Buffer<node_t>& nodes_data, const bool pair) :
      pair      (pair),
      nodes     (nodes_data),
      transform (nodes) { }

    Tck2nodes_base (const Tck2nodes_base& that) :
      pair      (that.pair),
      nodes     (that.nodes),
      transform (that.transform) { }

    virtual ~Tck2nodes_base() { }

    NodePair operator() (const Tractography::Streamline<float>& tck) const {
      assert (pair);
      VoxelType voxel (nodes);
      const node_t node_one = select_node (tck, voxel, false);
      const node_t node_two = select_node (tck, voxel, true);
      return std::make_pair (node_one, node_two);
    }

    bool operator() (const Streamline<>& tck, std::vector<node_t>& out) const {
      assert (!pair);
      VoxelType voxel (nodes);
      select_nodes (tck, voxel, out);
      return true;
    }

    bool provides_pair() const { return pair; }

  protected:
    bool pair;
    typedef Image::Buffer<node_t>::voxel_type VoxelType;

    Image::Buffer<node_t>& nodes;
    Image::Transform transform;

    virtual node_t select_node (const Tractography::Streamline<float>& tck, VoxelType& voxel, const bool end) const {
      throw Exception ("Calling empty virtual function Tck2nodes_base::select_node()");
    }

    virtual void select_nodes (const Streamline<>& tck, VoxelType& voxel, std::vector<node_t>& out) const {
      throw Exception ("Calling empty virtual function Tck2nodes_base::select_nodes()");
    }

};




// Specific implementations of assignment methodologies

// Most basic: look up the voxel value at the voxel containing the streamline endpoint
class Tck2nodes_end_voxels : public Tck2nodes_base {

  public:
    Tck2nodes_end_voxels (Image::Buffer<node_t>& nodes_data) :
      Tck2nodes_base (nodes_data, true) { }

    Tck2nodes_end_voxels (const Tck2nodes_end_voxels& that) :
      Tck2nodes_base (that) { }

    ~Tck2nodes_end_voxels() { }

  private:
    node_t select_node (const Tractography::Streamline<float>& tck, VoxelType& voxel, const bool end) const override;

};





// Radial search
class Tck2nodes_radial : public Tck2nodes_base {

  public:
    Tck2nodes_radial (Image::Buffer<node_t>& nodes_data, const float radius) :
      Tck2nodes_base (nodes_data, true),
      max_dist       (radius),
      max_add_dist   (std::sqrt (Math::pow2 (0.5 * nodes.vox(2)) + Math::pow2 (0.5 * nodes.vox(1)) + Math::pow2 (0.5 * nodes.vox(0))))
    {
      initialise_search ();
    }

    Tck2nodes_radial (const Tck2nodes_radial& that) :
      Tck2nodes_base (that),
      radial_search  (that.radial_search),
      max_dist       (that.max_dist),
      max_add_dist   (that.max_add_dist) { }

    ~Tck2nodes_radial() { }

  private:
    node_t select_node (const Tractography::Streamline<float>& tck, VoxelType& voxel, const bool end) const override;

    void initialise_search ();
    std::vector< Point<int> > radial_search;
    const float max_dist;
    // Distances are sub-voxel from the precise streamline termination point, so the search order is imperfect.
    //   This parameter controls when to stop the radial search because no voxel within the search space can be closer
    //   than the closest voxel with non-zero node index processed thus far.
    const float max_add_dist;

    friend class Tck2nodes_visitation;

};



// Do a reverse-search from the track endpoints inwards
class Tck2nodes_revsearch : public Tck2nodes_base
{

  public:
    Tck2nodes_revsearch (Image::Buffer<node_t>& nodes_data, const float length) :
      Tck2nodes_base (nodes_data, true),
      max_dist       (length) { }

    Tck2nodes_revsearch (const Tck2nodes_revsearch& that) :
      Tck2nodes_base (that),
      max_dist       (that.max_dist) { }

    ~Tck2nodes_revsearch() { }

  private:
    node_t select_node (const Tractography::Streamline<float>& tck, VoxelType& voxel, const bool end) const override;

    const float max_dist;

};



// Forward search - form a diamond-like shape emanating from the streamline endpoint in the direction of the tangent
class Tck2nodes_forwardsearch : public Tck2nodes_base
{

  public:
    Tck2nodes_forwardsearch (Image::Buffer<node_t>& nodes_data, const float length) :
      Tck2nodes_base (nodes_data, true),
      max_dist       (length),
      angle_limit    (Math::pi_4) { } // 45 degree limit

    Tck2nodes_forwardsearch (const Tck2nodes_forwardsearch& that) :
      Tck2nodes_base (that),
      max_dist       (that.max_dist),
      angle_limit    (that.angle_limit) { }

    ~Tck2nodes_forwardsearch() { }

  private:
    node_t select_node (const Tractography::Streamline<float>& tck, VoxelType& voxel, const bool end) const override;

    const float max_dist;
    const float angle_limit;

    float get_cf (const Point<float>&, const Point<float>&, const Point<int>&) const;

};





// Class that obtains a list of all nodes overlapped by the streamline
class Tck2nodes_all_voxels : public Tck2nodes_base
{
  public:
    Tck2nodes_all_voxels (Image::Buffer<node_t>& nodes_data) :
      Tck2nodes_base (nodes_data, false) { }

    Tck2nodes_all_voxels (const Tck2nodes_all_voxels& that) :
      Tck2nodes_base (that) { }

    ~Tck2nodes_all_voxels() { }

  private:
    void select_nodes (const Streamline<>&, VoxelType&, std::vector<node_t>&) const override;

};






}
}
}
}


#endif

