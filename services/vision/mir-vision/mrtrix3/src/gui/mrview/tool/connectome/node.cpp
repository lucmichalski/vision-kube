/*
   Copyright 2014 Brain Research Institute, Melbourne, Australia

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

#include "gui/mrview/tool/connectome/node.h"

#include <vector>

#include "exception.h"

#include "mesh/vox2mesh.h"

namespace MR
{
  namespace GUI
  {
    namespace MRView
    {
      namespace Tool
      {




        Node::Node (const Point<float>& com, const size_t vol, const size_t pixheight, std::shared_ptr< MR::Image::BufferScratch<bool> >& image) :
            centre_of_mass (com),
            volume (vol),
            mask (image),
            name (image->name()),
            size (1.0f),
            colour (0.5f, 0.5f, 0.5f),
            alpha (1.0f),
            visible (true),
            pixmap (pixheight, pixheight)
        {
          pixmap.fill (QColor (128, 128, 128));
        }

        Node::Node () :
            centre_of_mass (),
            volume (0),
            size (0.0f),
            colour (0.0f, 0.0f, 0.0f),
            alpha (0.0f),
            visible (false),
            pixmap (12, 12)
        {
          pixmap.fill (QColor (0, 0, 0));
        }











        Node::Mesh::Mesh (MR::Mesh::Mesh& in) :
            count (3 * in.num_triangles())
        {
          std::vector<float> vertices;
          vertices.reserve (3 * in.num_vertices());
          for (size_t v = 0; v != in.num_vertices(); ++v) {
            for (size_t axis = 0; axis != 3; ++axis)
              vertices.push_back (in.vert(v)[axis]);
          }
          vertex_buffer.gen();
          vertex_buffer.bind (gl::ARRAY_BUFFER);
          if (vertices.size())
            gl::BufferData (gl::ARRAY_BUFFER, vertices.size() * sizeof (float), &vertices[0], gl::STATIC_DRAW);

          if (!in.have_normals())
            in.calculate_normals();
          std::vector<float> normals;
          normals.reserve (3 * in.num_vertices());
          for (size_t n = 0; n != in.num_vertices(); ++n) {
            for (size_t axis = 0; axis != 3; ++axis)
              normals.push_back (in.norm(n)[axis]);
          }
          normal_buffer.gen();
          normal_buffer.bind (gl::ARRAY_BUFFER);
          if (normals.size())
            gl::BufferData (gl::ARRAY_BUFFER, normals.size() * sizeof (float), &normals[0], gl::STATIC_DRAW);

          vertex_array_object.gen();
          vertex_array_object.bind();
          vertex_buffer.bind (gl::ARRAY_BUFFER);
          gl::EnableVertexAttribArray (0);
          gl::VertexAttribPointer (0, 3, gl::FLOAT, gl::FALSE_, 0, (void*)(0));
          normal_buffer.bind (gl::ARRAY_BUFFER);
          gl::EnableVertexAttribArray (1);
          gl::VertexAttribPointer (1, 3, gl::FLOAT, gl::FALSE_, 0, (void*)(0));

          std::vector<unsigned int> indices;
          indices.reserve (3 * in.num_triangles());
          for (size_t i = 0; i != in.num_triangles(); ++i) {
            for (size_t v = 0; v != 3; ++v)
              indices.push_back (in.tri(i)[v]);
          }
          index_buffer.gen();
          index_buffer.bind();
          if (indices.size())
            gl::BufferData (gl::ELEMENT_ARRAY_BUFFER, indices.size() * sizeof (unsigned int), &indices[0], gl::STATIC_DRAW);
        }

        Node::Mesh::Mesh (Mesh&& that) :
            count (that.count),
            vertex_buffer (std::move (that.vertex_buffer)),
            normal_buffer (std::move (that.normal_buffer)),
            vertex_array_object (std::move (that.vertex_array_object)),
            index_buffer (std::move (that.index_buffer))
        {
          that.count = 0;
        }

        Node::Mesh::Mesh () :
            count (0) { }

        Node::Mesh& Node::Mesh::operator= (Node::Mesh&& that)
        {
          count = that.count; that.count = 0;
          vertex_buffer = std::move (that.vertex_buffer);
          normal_buffer = std::move (that.normal_buffer);
          vertex_array_object = std::move (that.vertex_array_object);
          index_buffer = std::move (that.index_buffer);
          return *this;
        }

        void Node::Mesh::render() const
        {
          assert (count);
          vertex_buffer.bind (gl::ARRAY_BUFFER);
          normal_buffer.bind (gl::ARRAY_BUFFER);
          vertex_array_object.bind();
          index_buffer.bind();
          gl::DrawElements (gl::TRIANGLES, count, gl::UNSIGNED_INT, (void*)0);
        }






      }
    }
  }
}





