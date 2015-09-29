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

#ifndef __gui_mrview_mode_slice_h__
#define __gui_mrview_mode_slice_h__

#include "app.h"
#include "gui/mrview/mode/base.h"

namespace MR
{
  namespace GUI
  {
    namespace MRView
    {
      namespace Mode
      {

        class Slice : public Base
        {
          public:
            Slice () :
              Base (FocusContrast | MoveTarget | TiltRotate) { }
            virtual ~Slice ();

            virtual void paint (Projection& with_projection);

            class Shader : public Displayable::Shader {
              public:
                virtual std::string vertex_shader_source (const Displayable& object);
                virtual std::string fragment_shader_source (const Displayable& object);
            } slice_shader;

          protected:
            void setup_draw (int axis, Projection& with_projection);
            virtual void draw_plane_primitive (int axis, Displayable::Shader& shader_program, Projection& with_projection);
            void draw_plane (int axis, Displayable::Shader& shader_program, Projection& with_projection);
        };

      }
    }
  }
}

#endif




