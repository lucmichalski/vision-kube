/*
    Copyright 2008 Brain Research Institute, Melbourne, Australia

    Written by J-Donald Tournier, 27/06/08.

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

#include "command.h"
#include "progressbar.h"
#include "image/buffer.h"
#include "image/voxel.h"
#include "image/axis.h"
#include "image/threaded_copy.h"
#include "image/adapter/extract.h"
#include "image/adapter/permute_axes.h"
#include "image/stride.h"
#include "dwi/gradient.h"


using namespace MR;
using namespace App;

void usage ()
{
  DESCRIPTION
  + "perform conversion between different file types and optionally "
  "extract a subset of the input image."

  + "If used correctly, this program can be a very useful workhorse. "
  "In addition to converting images between different formats, it can "
  "be used to extract specific studies from a data set, extract a "
  "specific region of interest, or flip the images.";

  ARGUMENTS
  + Argument ("input", "the input image.").type_image_in ()
  + Argument ("output", "the output image.").type_image_out ();

  OPTIONS
  + Option ("coord",
            "extract data from the input image only at the coordinates specified.")
  .allow_multiple()
  + Argument ("axis").type_integer (0, 0, std::numeric_limits<int>::max())
  + Argument ("coord").type_sequence_int()

  + Option ("vox",
            "change the voxel dimensions of the output image. The new sizes should "
            "be provided as a comma-separated list of values. Only those values "
            "specified will be changed. For example: 1,,3.5 will change the voxel "
            "size along the x & z axes, and leave the y-axis voxel size unchanged.")
  + Argument ("sizes").type_sequence_float()

  + Option ("axes",
            "specify the axes from the input image that will be used to form the output "
            "image. This allows the permutation, ommission, or addition of axes into the "
            "output image. The axes should be supplied as a comma-separated list of axes. "
            "Any ommitted axes must have dimension 1. Axes can be inserted by supplying "
            "-1 at the corresponding position in the list.")
  + Argument ("axes").type_sequence_int()

  + Option ("scaling",
            "specify the data scaling parameters used to rescale the intensity values. "
            "These take the form of a comma-separated 2-vector of floating-point values, "
            "corresponding to offset & scale, with final intensity values being given by "
            "offset + scale * stored_value. "
            "By default, the values in the input image header are passed through to the "
            "output image header when writing to an integer image; when writing to a "
            "floating-point image, these values are reset to 0,1 (no scaling). To force "
            "mrconvert to preserve the input image's scaling parameters even for "
            "floating-point outputs, use '-scaling preserve'")
  + Argument ("values").type_sequence_float()

  + Image::Stride::StrideOption

  + DataType::options()

  + DWI::GradImportOptions (false)
  + DWI::GradExportOptions();
}




template <class InfoType>
inline std::vector<int> set_header (Image::Header& header, const InfoType& input)
{
  // need to preserve dataype, already parsed from command-line:
  auto datatype = header.datatype();
  header.info() = input.info();
  header.datatype() = datatype;

  if (get_options ("grad").size() || get_options ("fslgrad").size())
    header.DW_scheme() = DWI::get_DW_scheme<float> (header);

  auto opt = get_options ("axes");
  std::vector<int> axes;
  if (opt.size()) {
    axes = opt[0][0];
    header.set_ndim (axes.size());
    for (size_t i = 0; i < axes.size(); ++i) {
      if (axes[i] >= static_cast<int> (input.ndim()))
        throw Exception ("axis supplied to option -axes is out of bounds");
      header.dim(i) = axes[i] < 0 ? 1 : input.dim (axes[i]);
    }
  } else {
    header.set_ndim (input.ndim());
    axes.assign (input.ndim(), 0);
    for (size_t i = 0; i < axes.size(); ++i) {
      axes[i] = i;
      header.dim (i) = input.dim (i);
    }
  }

  opt = get_options ("vox");
  if (opt.size()) {
    std::vector<float> vox = opt[0][0];
    if (vox.size() > header.ndim())
      throw Exception ("too many axes supplied to -vox option");
    for (size_t n = 0; n < vox.size(); ++n) {
      if (std::isfinite (vox[n]))
        header.vox(n) = vox[n];
    }
  }

  Image::Stride::set_from_command_line (header);

  return axes;
}




template <typename T>
inline void copy_permute (Image::Header& header_in, Image::Header& header_out, const std::vector< std::vector<int> >& pos, const std::string& output_filename)
{
  typedef Image::Buffer<T> buffer_type;
  typedef typename buffer_type::voxel_type voxel_type;
  typedef Image::Adapter::Extract<voxel_type> extract_type;

  buffer_type buffer_in (header_in);
  voxel_type in = buffer_in.voxel();


  if (pos.empty()) {

    const std::vector<int> axes = set_header (header_out, in);
    buffer_type buffer_out (output_filename, header_out);
    voxel_type out = buffer_out.voxel();
    DWI::export_grad_commandline (buffer_out);

    Image::Adapter::PermuteAxes<voxel_type> perm (in, axes);
    Image::threaded_copy_with_progress (perm, out, 2);

  } else {

    extract_type extract (in, pos);
    const std::vector<int> axes = set_header (header_out, extract);
    buffer_type buffer_out (output_filename, header_out);
    voxel_type out = buffer_out.voxel();
    DWI::export_grad_commandline (buffer_out);

    Image::Adapter::PermuteAxes<extract_type> perm (extract, axes);
    Image::threaded_copy_with_progress (perm, out, 2);

  }

}










void run ()
{
  Image::Header header_in (argument[0]);

  Image::Header header_out (header_in);
  header_out.datatype() = DataType::from_command_line (header_out.datatype());
  if (!header_out.datatype().is_floating_point())
    header_out.set_intensity_scaling (header_in);

  if (header_in.datatype().is_complex() && !header_out.datatype().is_complex())
    WARN ("requested datatype is real but input datatype is complex - imaginary component will be ignored");

  Options opt = get_options ("coord");
  std::vector< std::vector<int> > pos;
  if (opt.size()) {
    pos.assign (header_in.ndim(), std::vector<int>());
    for (size_t n = 0; n < opt.size(); n++) {
      int axis = opt[n][0];
      if (axis >= (int)header_in.ndim())
        throw Exception ("axis " + str(axis) + " provided with -coord option is out of range of input image");
      if (pos[axis].size())
        throw Exception ("\"coord\" option specified twice for axis " + str (axis));
      pos[axis] = parse_ints (opt[n][1], header_in.dim(axis)-1);
      if (axis == 3 && header_in.DW_scheme().is_set()) {
        Math::Matrix<float>& grad (header_in.DW_scheme());
        if ((int)grad.rows() != header_in.dim(3)) {
          WARN ("Diffusion encoding of input file does not match number of image volumes; omitting gradient information from output image");
          header_out.DW_scheme().clear();
        }
        else {
          Math::Matrix<float> extract_grad (pos[3].size(), 4);
          for (size_t dir = 0; dir != pos[3].size(); ++dir)
            extract_grad.row(dir) = grad.row((pos[3])[dir]);
          header_out.DW_scheme() = extract_grad;
        }
      }
    }

    for (size_t n = 0; n < header_in.ndim(); ++n) {
      if (pos[n].empty()) {
        pos[n].resize (header_in.dim (n));
        for (size_t i = 0; i < pos[n].size(); i++)
          pos[n][i] = i;
      }
    }
  }


  opt = get_options ("scaling");
  if (opt.size()) {
    if (lowercase (opt[0][0]) == "preserve")
      header_out.set_intensity_scaling (header_in);
    else {
      std::vector<float> scaling = opt[0][0];
      if (scaling.size() != 2) 
        throw Exception ("-scaling option expects comma-separated 2-vector of floating-point values");
      header_out.intensity_offset() = scaling[0];
      header_out.intensity_scale() = scaling[1];
    }
  }

  if (!std::isfinite (header_out.intensity_offset()) || !std::isfinite (header_out.intensity_scale()) || header_out.intensity_scale() == 0.0)
    WARN ("invalid scaling parameters (offset: " + str(header_out.intensity_offset()) + ", scale: " + str(header_out.intensity_scale()) + ")");


  if (header_out.intensity_offset() == 0.0 && header_out.intensity_scale() == 1.0 && !header_out.datatype().is_floating_point()) {
    switch (header_out.datatype()() & DataType::Type) {
      case DataType::Bit:
      case DataType::UInt8:
      case DataType::UInt16:
      case DataType::UInt32:
        if (header_out.datatype().is_signed())
          copy_permute<int32_t> (header_in, header_out, pos, argument[1]);
        else
          copy_permute<uint32_t> (header_in, header_out, pos, argument[1]);
        break;
      case DataType::UInt64:
        if (header_out.datatype().is_signed())
          copy_permute<int64_t> (header_in, header_out, pos, argument[1]);
        else
          copy_permute<uint64_t> (header_in, header_out, pos, argument[1]);
        break;
      case DataType::Undefined: throw Exception ("invalid output image data type"); break;

    }
  }
  else {
    if (header_out.datatype().is_complex())
      copy_permute<cdouble> (header_in, header_out, pos, argument[1]);
    else
      copy_permute<double> (header_in, header_out, pos, argument[1]);
  }

}

