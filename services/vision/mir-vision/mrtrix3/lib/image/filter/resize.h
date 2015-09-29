/*
    Copyright 2012 Brain Research Institute, Melbourne, Australia

    Written by David Raffelt, 10/08/2012.

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

#ifndef __image_filter_resize_h__
#define __image_filter_resize_h__

#include "image/info.h"
#include "image/filter/reslice.h"
#include "image/filter/smooth.h"
#include "image/interp/nearest.h"
#include "image/interp/linear.h"
#include "image/interp/cubic.h"
#include "image/interp/sinc.h"
#include "image/buffer_scratch.h"
#include "image/copy.h"

namespace MR
{
  namespace Image
  {
    namespace Filter
    {
      /** \addtogroup Filters
      @{ */

      /*! Resize an image
       *
       *  Note that if the image is 4D, then only the first 3 dimensions can be resized.
       *
       *  Also note that if the image is down-sampled, the appropriate smoothing is automatically applied.
       *  using Gaussian smoothing.
       *
       * Typical usage:
       * \code
       * Image::BufferPreload<float> src_data (argument[0]);
       * auto src = src_data.voxel();
       * Image::Filter::Resize resize_filter (src);
       * float scale = 0.5;
       * resize_filter.set_scale_factor (scale);
       *
       * Image::Header header (src_data);
       * header.info() = resize_filter.info();
       * header.datatype() = src_data.datatype();
       *
       * Image::Buffer<float> dest_data (argument[1], src_data);
       * auto dest = dest_data.voxel();
       *
       * resize_filter (src, dest);
       *
       * \endcode
       */
      class Resize : public Base
      {

        public:
          template <class InfoType>
          Resize (const InfoType& in) :
              Base (in),
              interp_type (2) { }


          void set_voxel_size (float size)
          {
            std::vector <float> voxel_size (3, size);
            set_voxel_size (voxel_size);
          }


          void set_voxel_size (const std::vector<float>& voxel_size)
          {
            if (voxel_size.size() != 3)
              throw Exception ("the voxel size must be defined using a value for all three dimensions.");

            std::vector<float> original_extent(3);
            for (size_t j = 0; j < 3; ++j) {
              if (voxel_size[j] <= 0.0)
                throw Exception ("the voxel size must be larger than zero");
              original_extent[j] = axes_[j].dim * axes_[j].vox;
              axes_[j].dim = std::round (original_extent[j] / voxel_size[j] - 0.0001); // round down at .5
              // Here we adjust the translation to ensure the image extent is centered wrt the original extent.
              // This is important when the new voxel size is not an exact multiple of the original extent
              for (size_t i = 0; i < 3; ++i)
                transform_(i,3) += 0.5 * ((voxel_size[j] - axes_[j].vox) + (original_extent[j] - axes_[j].dim * voxel_size[j])) * transform_(i,j);
              axes_[j].vox = voxel_size[j];
            }
          }


          void set_size (const std::vector<int>& image_res)
          {
            if (image_res.size() != 3)
              throw Exception ("the image resolution must be defined for 3 spatial dimensions");
            std::vector<float> new_voxel_size (3);
            for (size_t d = 0; d < 3; ++d) {
              if (image_res[d] <= 0)
                throw Exception ("the image resolution must be larger that zero for all 3 spatial dimensions");
              new_voxel_size[d] = (this->dim(d) * this->vox(d)) / image_res[d];
            }
            set_voxel_size (new_voxel_size);
          }


          void set_scale_factor (float scale)
          {
            set_scale_factor (std::vector<float> (3, scale));
          }


          void set_scale_factor (const std::vector<float> & scale)
          {
            if (scale.size() != 3)
              throw Exception ("a scale factor for each spatial dimension is required");
            std::vector<float> new_voxel_size (3);
            for (size_t d = 0; d < 3; ++d) {
              if (scale[d] <= 0.0)
                throw Exception ("the scale factor must be larger than zero");
              new_voxel_size[d] = (this->dim(d) * this->vox(d)) / std::ceil (this->dim(d) * scale[d]);
            }
            set_voxel_size (new_voxel_size);
          }


          void set_interp_type (int type) {
            interp_type = type;
          }


          template <class InputVoxelType, class OutputVoxelType>
            void operator() (InputVoxelType& input, OutputVoxelType& output)
            {

              bool do_smoothing = false;
              std::vector<float> stdev (input.ndim(), 0.0);
              for (unsigned int d = 0; d < 3; ++d) {
                float scale_factor = (float)input.vox(d) / (float)output.vox(d);
                if (scale_factor < 1.0) {
                  do_smoothing = true;
                  stdev[d] = 1.0 / (2.0 * scale_factor);
                }
              }


              if (do_smoothing) {
                Filter::Smooth smooth_filter (input);
                smooth_filter.set_stdev (stdev);
                BufferScratch<float> smoothed_data (input);
                auto smoothed_voxel = smoothed_data.voxel();
                {
                  LogLevelLatch log_level (0);
                  smooth_filter (input, smoothed_voxel);
                }
                switch (interp_type) {
                case 0:
                  reslice <Image::Interp::Nearest> (smoothed_voxel, output);
                  break;
                case 1:
                  reslice <Image::Interp::Linear> (smoothed_voxel, output);
                  break;
                case 2:
                  reslice <Image::Interp::Cubic> (smoothed_voxel, output);
                  break;
                case 3:
                  reslice <Image::Interp::Sinc> (smoothed_voxel, output);
                  break;
                default:
                  assert (0);
                  break;
                }
              } else {
                switch (interp_type) {
                  case 0:
                    reslice <Image::Interp::Nearest> (input, output);
                    break;
                  case 1:
                    reslice <Image::Interp::Linear> (input, output);
                    break;
                  case 2:
                    reslice <Image::Interp::Cubic> (input, output);
                    break;
                  case 3:
                    reslice <Image::Interp::Sinc> (input, output);
                    break;
                  default:
                    assert (0);
                    break;
                }
              }
            }

        protected:
          int interp_type;
      };
      //! @}
    }
  }
}


#endif
