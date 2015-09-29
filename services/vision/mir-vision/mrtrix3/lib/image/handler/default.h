/*
   Copyright 2009 Brain Research Institute, Melbourne, Australia

   Written by J-Donald Tournier, 19/08/09.

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

#ifndef __image_handler_default_h__
#define __image_handler_default_h__

#include "types.h"
#include "image/handler/base.h"
#include "file/mmap.h"

namespace MR
{
  namespace Image
  {

    namespace Handler
    {

      class Default : public Base
      {
        public:
          Default (const Image::Header& header) : 
            Base (header),
            bytes_per_segment (0) { }
          ~Default () { close(); }

        protected:
          std::vector<std::shared_ptr<File::MMap> > mmaps;
          int64_t bytes_per_segment;

          virtual void load ();
          virtual void unload ();

          void map_files ();
          void copy_to_mem ();
          void copy_to_files ();

      };

    }
  }
}

#endif


