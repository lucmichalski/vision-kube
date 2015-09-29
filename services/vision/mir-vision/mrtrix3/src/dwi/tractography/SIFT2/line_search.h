/*
    Copyright 2011 Brain Research Institute, Melbourne, Australia

    Written by Robert Smith, 2013.

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



#ifndef __dwi_tractography_sift2_line_search_h__
#define __dwi_tractography_sift2_line_search_h__


#include <vector>

#include "dwi/tractography/SIFT/track_contribution.h"
#include "dwi/tractography/SIFT/types.h"



namespace MR {
  namespace DWI {
    namespace Tractography {
      namespace SIFT2 {



      class TckFactor;



      // New line search functor for when per-streamline projections and per-fixel correlation terms are not calculated
      // Instead, the correlation term for the line search is derived using the TD fraction only
      class LineSearchFunctor
      {

        public:

          class Result
          {
            public:
            Result() : cost (0.0), first_deriv (0.0), second_deriv (0.0), third_deriv (0.0) { }
            Result& operator+= (const Result& that) { cost += that.cost; first_deriv += that.first_deriv; second_deriv += that.second_deriv; third_deriv += that.third_deriv; return *this; }
            Result& operator*= (const float i) { cost *= i; first_deriv *= i; second_deriv *= i; third_deriv *= i; return *this; }
            double cost, first_deriv, second_deriv, third_deriv;
            bool valid() const { return std::isfinite(cost) && std::isfinite(first_deriv) && std::isfinite(second_deriv) && std::isfinite(third_deriv); }
          };

          LineSearchFunctor (const SIFT::track_t, const TckFactor&);


          // Interfaces for line searches
          Result get        (const float) const;
          double operator() (const float) const;


        protected:

          // Necessary information for those fixels traversed by this streamline
          class Fixel
          {
            public:
            Fixel (const SIFT::Track_fixel_contribution&, const TckFactor&, const float, const float, const float);
            void set_damping (const float i) { dTD_dFs *= i; }
            uint32_t index;
            double length, PM, TD, cost_frac, SL_eff, dTD_dFs, meanFs, expmeanFs, FOD;
          };


          const SIFT::track_t track_index;
          const double mu;
          const double Fs;
          const double reg_tik, reg_tv;

          std::vector<Fixel> fixels;

      };





      }
    }
  }
}


#endif

