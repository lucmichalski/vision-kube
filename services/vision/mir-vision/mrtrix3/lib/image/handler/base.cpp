/*
   Copyright 2009 Brain Research Institute, Melbourne, Australia

   Written by J-Donald Tournier, 03/09/09.

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

#include "image/handler/base.h"
#include "image/header.h"

namespace MR
{
  namespace Image
  {
    namespace Handler
    {
      Base::Base (const Image::Header& header) : 
        name (header.name()), 
        datatype (header.datatype()),
        segsize (Image::voxel_count (header)),
        is_new (false),
        writable (false) { }


      Base::~Base () { }


      void Base::open ()
      {
        if (addresses.size())
          return;

        load();
        DEBUG ("image \"" + name + "\" loaded");
      }



      void Base::close ()
      {
        if (addresses.empty())
          return;

        unload();
        DEBUG ("image \"" + name + "\" unloaded");
        addresses.clear();
      }


    }
  }
}

