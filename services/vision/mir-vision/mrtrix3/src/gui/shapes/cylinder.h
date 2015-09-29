/*
    Copyright 2008 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith, 2015.

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

#ifndef __gui_shapes_cylinder_h__
#define __gui_shapes_cylinder_h__

#include <vector>

#include "math/vector.h"

#include "gui/opengl/gl.h"
#include "gui/opengl/gl_core_3_3.h"

namespace MR
{
  namespace GUI
  {
    namespace Shapes
    {


    class Cylinder
    {
      public:
        Cylinder () : num_indices (0) { }

        void LOD (const size_t);

        size_t num_indices;
        GL::VertexBuffer vertex_buffer, normal_buffer;
        GL::IndexBuffer index_buffer;

    };


    }
  }
}

#endif

