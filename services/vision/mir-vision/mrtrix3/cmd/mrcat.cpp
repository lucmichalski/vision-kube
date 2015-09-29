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
#include "image/buffer.h"
#include "image/voxel.h"
#include "image/loop.h"
#include "progressbar.h"


using namespace MR;
using namespace App;

void usage () {
DESCRIPTION
  + "concatenate several images into one";

ARGUMENTS
  + Argument ("image1", "the first input image.")
  .type_image_in()

  + Argument ("image2", "the second input image.")
  .type_image_in()
  .allow_multiple()

  + Argument ("output", "the output image.")
  .type_image_out ();

OPTIONS
  + Option ("axis",
  "specify axis along which concatenation should be performed. By default, "
  "the program will use the last non-singleton, non-spatial axis of any of "
  "the input images - in other words axis 3 or whichever axis (greater than 3) "
  "of the input images has size greater than one.")
  + Argument ("axis").type_integer (0, std::numeric_limits<int>::max(), std::numeric_limits<int>::max())

  + DataType::options();
}


typedef float value_type;


void run () {
  int axis = -1;

  Options opt = get_options ("axis");
  if (opt.size())
    axis = opt[0][0];

  int num_images = argument.size()-1;
  std::vector<std::unique_ptr<Image::Buffer<value_type>>> in (num_images);
  in[0].reset (new Image::Buffer<value_type> (argument[0]));
  Image::ConstHeader& header_in (*in[0]);

  int ndims = 0;
  int last_dim;

  for (int i = 1; i < num_images; i++) {
    in[i].reset (new Image::Buffer<value_type> (argument[i]));
    for (last_dim = in[i]->ndim()-1; in[i]->dim (last_dim) <= 1 && last_dim >= 0; last_dim--);
    if (last_dim > ndims)
      ndims = last_dim;
  }

  if (axis < 0) axis = std::max (3, ndims);
  ++ndims;

  for (int i = 0; i < ndims; i++)
    if (i != axis)
      for (int n = 0; n < num_images; n++)
        if (in[0]->dim (i) != in[n]->dim (i))
          throw Exception ("dimensions of input images do not match");

  if (axis >= ndims) ndims = axis+1;

  Image::Header header_out (header_in);
  header_out.set_ndim (ndims);

  for (size_t i = 0; i < header_out.ndim(); i++) {
    if (header_out.dim (i) <= 1) {
      for (int n = 0; n < num_images; n++) {
        if (in[n]->ndim() > i) {
          header_out.dim(i) = in[n]->dim (i);
          header_out.vox(i) = in[n]->vox (i);
          break;
        }
      }
    }
  }


  {
    size_t axis_dim = 0;
    for (int n = 0; n < num_images; n++) {
      if (in[n]->datatype().is_complex())
        header_out.datatype() = DataType::CFloat32;
      axis_dim += in[n]->ndim() > size_t (axis) ? (in[n]->dim (axis) > 1 ? in[n]->dim (axis) : 1) : 1;
    }
    header_out.dim(axis) = axis_dim;
  }

  header_out.datatype() = DataType::from_command_line (header_out.datatype());
  


  if (axis > 2) { // concatenate DW schemes
    size_t nrows = 0;
    for (int n = 0; n < num_images; ++n) {
      if (in[n]->DW_scheme().rows() == 0 ||  
          in[n]->DW_scheme().columns() != 4) {
        nrows = 0;
        break;
      }   
      nrows += in[n]->DW_scheme().rows();
    }   

    if (nrows) {
      header_out.DW_scheme().allocate (nrows, 4); 
      int row = 0;
      for (int n = 0; n < num_images; ++n) 
        for (size_t i = 0; i < in[n]->DW_scheme().rows(); ++i, ++row)
          for (size_t j = 0; j < 4; ++j) 
            header_out.DW_scheme()(row,j) = in[n]->DW_scheme()(i,j);
    }   
  }



  Image::Buffer<value_type> data_out (argument[num_images], header_out);
  auto out_vox = data_out.voxel();
  int axis_offset = 0;


  for (int i = 0; i < num_images; i++) {
    auto in_vox = in[i]->voxel();

    auto copy_func = [&axis, &axis_offset](decltype(in_vox)& in, decltype(out_vox)& out)
    {
      out[axis] = axis < int(in.ndim()) ? in[axis] + axis_offset : axis_offset;
      out.value() = in.value();
    };

    Image::ThreadedLoop ("concatenating \"" + in_vox.name() + "\"...", in_vox, 0, std::min (in_vox.ndim(), out_vox.ndim()))
      .run (copy_func, in_vox, out_vox);
    if (axis < int(in_vox.ndim()))
      axis_offset += in_vox.dim (axis);
    else {
      ++axis_offset;
      out_vox[axis] = axis_offset;
    }
  }
}

