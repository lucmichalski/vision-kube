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


#include "file/config.h"
#include "file/ofstream.h"
#include "file/path.h"
#include "file/utils.h"
#include "file/mmap.h"
#include "image/utils.h"
#include "image/format/list.h"
#include "image/header.h"
#include "image/handler/default.h"
#include "get_set.h"

/*
 MRI format:
     Magic number:              MRI#         (4 bytes)
     Byte order specifier:    uint16_t = 1     (2 bytes)
     ...
     Elements:
       ID specifier:            uint32_t       (4 bytes)
       size:                    uint32_t       (4 bytes)
       contents:              unspecified  ('size' bytes)
     ...

*/

#define MRI_DATA       0x01
#define MRI_DIMENSIONS 0x02
#define MRI_ORDER      0x03
#define MRI_VOXELSIZE  0x04
#define MRI_COMMENT    0x05
#define MRI_TRANSFORM  0x06
#define MRI_DWSCHEME   0x07

namespace MR
{
  namespace Image
  {
    namespace Format
    {

      namespace
      {

        inline size_t char2order (char item, bool& forward)
        {
          switch (item) {
            case 'L':
              forward = true;
              return (0);
            case 'R':
              forward = false;
              return (0);
            case 'P':
              forward = true;
              return (1);
            case 'A':
              forward = false;
              return (1);
            case 'I':
              forward = true;
              return (2);
            case 'S':
              forward = false;
              return (2);
            case 'B':
              forward = true;
              return (3);
            case 'E':
              forward = false;
              return (3);
          }
          return (std::numeric_limits<size_t>::max());
        }


        inline char order2char (size_t axis, bool forward)
        {
          switch (axis) {
            case 0:
              if (forward) return ('L');
              else return ('R');
            case 1:
              if (forward) return ('P');
              else return ('A');
            case 2:
              if (forward) return ('I');
              else return ('S');
            case 3:
              if (forward) return ('B');
              else return ('E');
          }
          return ('\0');
        }


        inline size_t type (const uint8_t* pos, bool is_BE)
        {
          return (get<uint32_t> (pos, is_BE));
        }
        inline size_t size (const uint8_t* pos, bool is_BE)
        {
          return (get<uint32_t> (pos + sizeof (uint32_t), is_BE));
        }
        inline const uint8_t* data (const uint8_t* pos)
        {
          return (pos + 2*sizeof (uint32_t));
        }

        inline const uint8_t* next (const uint8_t* current_pos, bool is_BE)
        {
          return (current_pos + 2*sizeof (uint32_t) + size (current_pos, is_BE));
        }

        inline void write_tag (std::ostream& out, uint32_t Type, uint32_t Size, bool is_BE)
        {
          Type = ByteOrder::swap<uint32_t> (Type, is_BE);
          out.write ( (const char*) &Type, sizeof (uint32_t));
          Size = ByteOrder::swap<uint32_t> (Size, is_BE);
          out.write ( (const char*) &Size, sizeof (uint32_t));
        }

        template <typename T> inline void write (std::ostream& out, T val, bool is_BE)
        {
          val = ByteOrder::swap<T> (val, is_BE);
          out.write ( (const char*) &val, sizeof (T));
        }

        // needed to get around changes in hard-coded enum types in datatype.h:
        DataType fetch_datatype (uint8_t c) {
          uint8_t d = c & 0x07U;
          uint8_t t = c & ~(0x07U);
          if (d >= 0x05U) ++d;
          return { uint8_t (d | t) };
        }

        uint8_t store_datatype (DataType& dt) {
          uint8_t d = dt() & 0x07U;
          uint8_t t = dt() & ~(0x07U);
          if (d >= 0x05U) --d;
          return (d | t);
        }

      }








      std::shared_ptr<Handler::Base> MRI::read (Header& H) const
      {
        if (!Path::has_suffix (H.name(), ".mri"))
          return std::shared_ptr<Handler::Base>();

        File::MMap fmap (H.name());

        if (memcmp (fmap.address(), "MRI#", 4))
          throw Exception ("file \"" + H.name() + "\" is not in MRI format (unrecognised magic number)");

        bool is_BE = false;
        if (get<uint16_t> (fmap.address() + sizeof (int32_t), is_BE) == 0x0100U) is_BE = true;
        else if (get<uint16_t> (fmap.address() + sizeof (uint32_t), is_BE) != 0x0001U)
          throw Exception ("MRI file \"" + H.name() + "\" is badly formed (invalid byte order specifier)");

        H.set_ndim (4);

        size_t data_offset = 0;
        char* c;
        const uint8_t* current = fmap.address() + sizeof (int32_t) + sizeof (uint16_t);
        const uint8_t* last = fmap.address() + fmap.size() - 2*sizeof (uint32_t);

        while (current <= last) {
          switch (type (current, is_BE)) {
            case MRI_DATA:
              H.datatype() = fetch_datatype (data(current)[-4]);
              data_offset = current + 5 - (uint8_t*) fmap.address();
              break;
            case MRI_DIMENSIONS:
              H.dim(0) = get<uint32_t> (data (current), is_BE);
              H.dim(1) = get<uint32_t> (data (current) + sizeof (uint32_t), is_BE);
              H.dim(2) = get<uint32_t> (data (current) + 2*sizeof (uint32_t), is_BE);
              H.dim(3) = get<uint32_t> (data (current) + 3*sizeof (uint32_t), is_BE);
              break;
            case MRI_ORDER:
              c = (char*) data (current);
              for (size_t n = 0; n < 4; n++) {
                bool forward = true;
                size_t ax = char2order (c[n], forward);
                if (ax == std::numeric_limits<size_t>::max())
                  throw Exception ("invalid order specifier in MRI image \"" + H.name() + "\"");
                H.stride(ax) = n+1;
                if (!forward)
                  H.stride(ax) = -H.stride (ax);
              }
              break;
            case MRI_VOXELSIZE:
              H.vox(0) = get<float32> (data (current), is_BE);
              H.vox(1) = get<float32> (data (current) + sizeof (float32), is_BE);
              H.vox(2) = get<float32> (data (current) + 2*sizeof (float32), is_BE);
              break;
            case MRI_COMMENT:
              H.comments().push_back (std::string (reinterpret_cast<const char*> (data (current)), size (current, is_BE)));
              break;
            case MRI_TRANSFORM:
              H.transform().allocate (4,4);
              for (size_t i = 0; i < 4; ++i)
                for (size_t j = 0; j < 4; ++j)
                  H.transform() (i,j) = get<float32> (data (current) + (i*4 + j) *sizeof (float32), is_BE);
              break;
            case MRI_DWSCHEME:
              H.DW_scheme().allocate (size (current, is_BE) / (4*sizeof (float32)), 4);
              for (size_t i = 0; i < H.DW_scheme().rows(); ++i)
                for (size_t j = 0; j < 4; ++j)
                  H.DW_scheme() (i,j) = get<float32> (data (current) + (i*4 + j) *sizeof (float32), is_BE);
              break;
            default:
              WARN ("unknown header entity (" + str (type (current, is_BE))
                     + ", offset " + str (current - fmap.address())
                     + ") in image \"" + H.name() + "\" - ignored");
              break;
          }

          if (data_offset)
            break;

          current = next (current, is_BE);
        }


        if (!data_offset)
          throw Exception ("no data field found in MRI image \"" + H.name() + "\"");

        std::shared_ptr<Handler::Base> handler (new Handler::Default (H));
        handler->files.push_back (File::Entry (H.name(), data_offset));

        return handler;
      }





      bool MRI::check (Header& H, size_t num_axes) const
      {
        if (!Path::has_suffix (H.name(), ".mri"))
          return false;

        if (H.ndim() > num_axes && num_axes != 4)
          throw Exception ("MRTools format can only support 4 dimensions");

        H.set_ndim (num_axes);

        return true;
      }







      std::shared_ptr<Handler::Base> MRI::create (Header& H) const
      {
        File::OFStream out (H.name());

#ifdef MRTRIX_BYTE_ORDER_BIG_ENDIAN
        bool is_BE = true;
#else
        bool is_BE = false;
#endif

        out.write ("MRI#", 4);
        write<uint16_t> (out, 0x01U, is_BE);

        write_tag (out, MRI_DIMENSIONS, 4*sizeof (uint32_t), is_BE);
        write<uint32_t> (out, H.dim (0), is_BE);
        write<uint32_t> (out, (H.ndim() > 1 ? H.dim (1) : 1), is_BE);
        write<uint32_t> (out, (H.ndim() > 2 ? H.dim (2) : 1), is_BE);
        write<uint32_t> (out, (H.ndim() > 3 ? H.dim (3) : 1), is_BE);

        write_tag (out, MRI_ORDER, 4*sizeof (uint8_t), is_BE);
        size_t n;
        char order[4];
        for (n = 0; n < H.ndim(); ++n)
          order[std::abs (H.stride (n))-1] = order2char (n, H.stride (n) >0);
        for (; n < 4; ++n)
          order[n] = order2char (n, true);
        out.write (order, 4);

        write_tag (out, MRI_VOXELSIZE, 3*sizeof (float32), is_BE);
        write<float> (out, H.vox (0), is_BE);
        write<float> (out, (H.ndim() > 1 ? H.vox (1) : 2.0f), is_BE);
        write<float> (out, (H.ndim() > 2 ? H.vox (2) : 2.0f), is_BE);

        for (size_t n = 0; n < H.comments().size(); n++) {
          size_t l = H.comments() [n].size();
          if (l) {
            write_tag (out, MRI_COMMENT, l, is_BE);
            out.write (H.comments() [n].c_str(), l);
          }
        }

        if (H.transform().is_set()) {
          write_tag (out, MRI_TRANSFORM, 16*sizeof (float32), is_BE);
          for (size_t i = 0; i < 4; ++i)
            for (size_t j = 0; j < 4; ++j)
              write<float> (out, H.transform() (i,j), is_BE);
        }

        if (H.DW_scheme().is_set()) {
          write_tag (out, MRI_DWSCHEME, 4*H.DW_scheme().rows() *sizeof (float32), is_BE);
          for (size_t i = 0; i < H.DW_scheme().rows(); ++i)
            for (size_t j = 0; j < 4; ++j)
              write<float> (out, H.DW_scheme() (i,j), is_BE);
        }

        write_tag (out, MRI_DATA, 1, is_BE);
        out.put (store_datatype (H.datatype()));

        size_t data_offset = out.tellp();
        out.close();

        std::shared_ptr<Handler::Base> handler (new Handler::Default (H));
        File::resize (H.name(), data_offset + Image::footprint(H));
        handler->files.push_back (File::Entry (H.name(), data_offset));

        return handler;
      }

    }
  }
}



