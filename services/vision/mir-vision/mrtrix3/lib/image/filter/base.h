/*
    Copyright 2012 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith 27/03/14.

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

#ifndef __image_filter_base_h__
#define __image_filter_base_h__

#include "progressbar.h" // May be needed for any derived classes that make use of the message string
#include "image/info.h"

namespace MR
{
  namespace Image
  {
    namespace Filter
    {
      /** \addtogroup Filters
      @{ */

      /*! A base class for defining image filters.
       *
       * The Filter::Base class defines the basic interface for
       * defining image filters. Since these filters can vary
       * substantially in their design and implementation, the
       * actual functionality of the Base class is almost zero
       * (above and beyond that of the ConstInfo class).
       *
       * It does however allow these filters to be initialised,
       * set up and run using base class pointers, and defines a
       * standardised functor interface that image filter classes
       * should ideally conform to.
       *
       */
      class Base : public ConstInfo
      {
        public:
          template <class InfoType>
          Base (const InfoType& in) :
              ConstInfo (in) { }

          template <class InfoType>
          Base (const InfoType& in, const std::string& message) :
              ConstInfo (in),
              message (message) { }

          virtual ~Base() { }


          void set_message (const std::string& s) { message = s; }


          template <class InputVoxelType, class OutputVoxelType>
          void operator() (InputVoxelType& in, OutputVoxelType& out)
          {
              throw Exception ("Running empty function Image::Filter::Base::operator()");
          }


        protected:
          std::string message;

      };
      //! @}
    }
  }
}


#endif
