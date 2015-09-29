/*
   Copyright 2011 Brain Research Institute, Melbourne, Australia

   Written by J-Donald Tournier and Robert E. Smith, 2011.

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

#ifndef __dwi_tractography_algorithms_tensor_det_h__
#define __dwi_tractography_algorithms_tensor_det_h__

#include "point.h"
#include "math/eigen.h"
#include "math/least_squares.h"
#include "dwi/gradient.h"
#include "dwi/tensor.h"
#include "dwi/tractography/tracking/method.h"
#include "dwi/tractography/tracking/shared.h"
#include "dwi/tractography/tracking/types.h"



namespace MR
{
  namespace DWI
  {
    namespace Tractography
    {
      namespace Algorithms
      {


    using namespace MR::DWI::Tractography::Tracking;


    class Tensor_Det : public MethodBase {
      public:
      class Shared : public SharedBase {
        public:
        Shared (const std::string& diff_path, DWI::Tractography::Properties& property_set) :
          SharedBase (diff_path, property_set) {

          if (is_act() && act().backtrack())
            throw Exception ("Backtracking not valid for deterministic algorithms");

          set_step_size (0.1);
          if (rk4) {
            INFO ("minimum radius of curvature = " + str(step_size / (max_angle_rk4 / (0.5 * Math::pi))) + " mm");
          } else {
            INFO ("minimum radius of curvature = " + str(step_size / ( 2.0 * sin (max_angle / 2.0))) + " mm");
          }

          properties["method"] = "TensorDet";

          Math::Matrix<float> grad = DWI::get_valid_DW_scheme<float> (source_buffer);

          grad2bmatrix (bmat, grad);
          Math::pinv (binv, bmat);
        }

        Math::Matrix<float> bmat, binv;
      };






      Tensor_Det (const Shared& shared) :
        MethodBase (shared),
        S (shared),
        source (S.source_voxel),
        eig (3),
        M (3,3),
        V (3,3),
        ev (3) { }




      bool init()
      {
        if (!get_data (source))
          return false;
        return do_init();
      }



      term_t next ()
      {
        if (!get_data (source))
          return Tracking::EXIT_IMAGE;
        return do_next();
      }


      float get_metric()
      {
        dwi2tensor (S.binv, &values[0]);
        return tensor2FA (&values[0]);
      }


      protected:
      const Shared& S;
      Tracking::Interpolator<SourceBufferType::voxel_type>::type source;
      Math::Eigen::SymmV<double> eig;
      Math::Matrix<double> M, V;
      Math::Vector<double> ev;

      void get_EV ()
      {
        M(0,0) = values[0];
        M(1,1) = values[1];
        M(2,2) = values[2];
        M(0,1) = M(1,0) = values[3];
        M(0,2) = M(2,0) = values[4];
        M(1,2) = M(2,1) = values[5];

        eig (ev, M, V);
        Math::Eigen::sort (ev, V);

        dir[0] = V(0,2);
        dir[1] = V(1,2);
        dir[2] = V(2,2);
      }


      bool do_init()
      {
        dwi2tensor (S.binv, &values[0]);

        if (tensor2FA (&values[0]) < S.init_threshold)
          return false;

        get_EV();

        return true;
      }


      term_t do_next()
      {

        dwi2tensor (S.binv, &values[0]);

        if (tensor2FA (&values[0]) < S.threshold)
          return BAD_SIGNAL;

        Point<value_type> prev_dir = dir;

        get_EV();

        value_type dot = prev_dir.dot (dir);
        if (std::abs (dot) < S.cos_max_angle)
          return HIGH_CURVATURE;

        if (dot < 0.0)
          dir = -dir;

        pos += dir * S.step_size;

        return CONTINUE;

      }


    };

      }
    }
  }
}

#endif


