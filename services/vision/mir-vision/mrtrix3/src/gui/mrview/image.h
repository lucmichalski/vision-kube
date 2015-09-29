/*
   Copyright 2009 Brain Research Institute, Melbourne, Australia

   Written by J-Donald Tournier, 13/11/09.

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

#ifndef __gui_mrview_image_h__
#define __gui_mrview_image_h__

#include "gui/opengl/gl.h"
#include "gui/mrview/volume.h"
#include "image/buffer.h"
#include "image/voxel.h"
#include "math/versor.h"
#include "image/interp/linear.h"
#include "image/interp/nearest.h"


namespace MR
{
  class ProgressBar;

  namespace GUI
  {
    class Projection;

    namespace MRView
    {

      class Window;

      class ImageBase : public Volume
      {
        public:
          ImageBase (const MR::Image::Info&);

          void render2D (Displayable::Shader& shader_program, const Projection& projection, const int plane, const int slice);
          void render3D (Displayable::Shader& shader_program, const Projection& projection, const float depth);

          virtual void update_texture2D (const int plane, const int slice) = 0;
          virtual void update_texture3D() = 0;

          void get_axes (const int plane, int& x, int& y) const;

        protected:
          GL::Texture texture2D[3];
          std::vector<ssize_t> position;

      };

      class Image : public ImageBase
      {
        public:
          Image (const MR::Image::Header& image_header);

          MR::Image::Header& header () { return buffer; }
          const MR::Image::Header& header () const { return buffer; }

          void update_texture2D (const int plane, const int slice) override;
          void update_texture3D() override;

          void request_render_colourbar(DisplayableVisitor& visitor) override
          { if(show_colour_bar) visitor.render_image_colourbar(*this); }

          typedef MR::Image::Buffer<cfloat> BufferType;
          typedef BufferType::voxel_type VoxelType;
          typedef MR::Image::Interp::Linear<VoxelType> InterpVoxelType;

        private:
          BufferType buffer;

        public:
          InterpVoxelType interp;
          VoxelType& voxel () { return interp; }
          cfloat trilinear_value (const Point<float> &scanner_point) {
            if (interp.scanner (scanner_point)) 
              return cfloat(NAN, NAN); 
            return interp.value();
          }
          cfloat nearest_neighbour_value (const Point<float> &scanner_point) {
            auto p = interp.scanner2voxel (scanner_point);
            ssize_t v[3] = { ssize_t (std::round (p[0])), ssize_t (std::round (p[1])), ssize_t (std::round (p[2])) };
            if (v[0] < 0 || v[0] >= voxel().dim(0) ||
                v[1] < 0 || v[1] >= voxel().dim(1) ||
                v[2] < 0 || v[2] >= voxel().dim(2)) 
              return cfloat(NAN, NAN); 
            voxel()[0] = v[0];
            voxel()[1] = v[1];
            voxel()[2] = v[2];
            return voxel().value();
          }

        private:
          bool volume_unchanged ();
          bool format_unchanged ();
          size_t guess_colourmap () const;

          template <typename T> void copy_texture_3D ();
          void copy_texture_3D_complex ();

      };


    }
  }
}

#endif

