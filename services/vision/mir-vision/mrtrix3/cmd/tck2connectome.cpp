/*
    Copyright 2011 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith, 2012.

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


#include <vector>
#include <set>

#include "command.h"
#include "memory.h"

#include "dwi/tractography/file.h"
#include "dwi/tractography/properties.h"
#include "dwi/tractography/weights.h"
#include "dwi/tractography/mapping/loader.h"
#include "dwi/tractography/connectome/connectome.h"
#include "dwi/tractography/connectome/edge_metrics.h"
#include "dwi/tractography/connectome/mapper.h"
#include "dwi/tractography/connectome/matrix.h"
#include "dwi/tractography/connectome/tck2nodes.h"


#include "image/buffer.h"
#include "image/loop.h"
#include "image/voxel.h"

#include "thread_queue.h"





using namespace MR;
using namespace App;
using namespace MR::DWI;
using namespace MR::DWI::Tractography;
using namespace MR::DWI::Tractography::Connectome;



void usage ()
{

	AUTHOR = "Robert E. Smith (r.smith@brain.org.au)";

  DESCRIPTION
  + "generate a connectome matrix from a streamlines file and a node parcellation image";

  ARGUMENTS
  + Argument ("tracks_in",      "the input track file").type_file_in()
  + Argument ("nodes_in",       "the input node parcellation image").type_image_in()
  + Argument ("connectome_out", "the output .csv file containing edge weights").type_file_out();


  OPTIONS
  + MR::DWI::Tractography::Connectome::AssignmentOption
  + MR::DWI::Tractography::Connectome::MetricOption

  + Tractography::TrackWeightsInOption

  + Option ("keep_unassigned", "By default, the program discards the information regarding those streamlines that are not successfully assigned to a node pair. "
                               "Set this option to keep these values (will be the first row/column in the output matrix)")

  + Option ("out_assignments", "output the node assignments of each streamline to a file")
    + Argument ("path").type_file_out()

  + Option ("zero_diagonal", "set all diagonal entries in the matrix to zero \n"
                             "(these represent streamlines that connect to the same node at both ends)")

  + Option ("vector", "output a vector representing connectivities from a given seed point to target nodes, "
                      "rather than a matrix of node-node connectivities");


};



void run ()
{

  Image::Buffer<node_t> nodes_data (argument[1]);
  auto nodes = nodes_data.voxel();

  // First, find out how many segmented nodes there are, so the matrix can be pre-allocated
  node_t max_node_index = 0;
  Image::LoopInOrder loop (nodes);
  for (auto i = loop (nodes); i; ++i) {
    if (nodes.value() > max_node_index)
      max_node_index = nodes.value();
  }

  // Check for node volume for all nodes
  std::vector<uint32_t> node_volumes (max_node_index + 1);
  for (auto i = loop (nodes); i; ++i) 
    ++node_volumes[nodes.value()];
  std::set<node_t> missing_nodes;
  for (size_t i = 1; i != node_volumes.size(); ++i) {
    if (!node_volumes[i])
      missing_nodes.insert (i);
  }
  if (missing_nodes.size()) {
    WARN ("The following nodes are missing from the parcellation image:");
    std::set<node_t>::iterator i = missing_nodes.begin();
    std::string list = str(*i);
    for (++i; i != missing_nodes.end(); ++i)
      list += ", " + str(*i);
    WARN (list);
    WARN ("(This may indicate poor parcellation image preparation, use of incorrect config file in labelconfig, or very poor registration)");
  }

  // Are we generating a matrix or a vector?
  const bool vector_output = get_options ("vector").size();

  // Get the metric & assignment mechanism for connectome construction
  std::unique_ptr<Metric_base>    metric    (load_metric (nodes_data));
  std::unique_ptr<Tck2nodes_base> tck2nodes (load_assignment_mode (nodes_data));

  // Prepare for reading the track data
  Tractography::Properties properties;
  Tractography::Reader<float> reader (argument[0], properties);

  // Initialise classes in preparation for multi-threading
  Mapping::TrackLoader loader (reader, properties["count"].empty() ? 0 : to<size_t>(properties["count"]), "Constructing connectome... ");
  Tractography::Connectome::Mapper mapper (*tck2nodes, *metric);
  Tractography::Connectome::Matrix connectome (max_node_index, vector_output);

  // Multi-threaded connectome construction
  if (tck2nodes->provides_pair()) {
    Thread::run_queue (
        loader,
        Thread::batch (Tractography::Streamline<float>()),
        Thread::multi (mapper),
        Thread::batch (Mapped_track_nodepair()),
        connectome);
  } else {
    Thread::run_queue (
        loader,
        Thread::batch (Tractography::Streamline<float>()),
        Thread::multi (mapper),
        Thread::batch (Mapped_track_nodelist()),
        connectome);
  }

  if (metric->scale_edges_by_streamline_count())
    connectome.scale_by_streamline_count();

  connectome.error_check (missing_nodes);

  if (!get_options ("keep_unassigned").size())
    connectome.remove_unassigned();

  if (get_options ("zero_diagonal").size())
    connectome.zero_diagonal();

  connectome.write (argument[2]);
  Options opt = get_options ("out_assignments");
  if (opt.size())
    connectome.write_assignments (opt[0][0]);

}
