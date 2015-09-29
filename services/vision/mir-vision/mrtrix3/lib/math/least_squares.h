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

#ifndef __math_least_squares_h__
#define __math_least_squares_h__

#include "math/cholesky.h"
#include "math/LU.h"

namespace MR
{
  namespace Math
  {

    namespace {
      template <typename ValueType> inline CBLAS_TRANSPOSE __transpose () { return CblasTrans; } 
#ifdef __math_complex_h__
      template <> inline CBLAS_TRANSPOSE __transpose<cfloat> () { return CblasConjTrans; }
      template <> inline CBLAS_TRANSPOSE __transpose<cdouble> () { return CblasConjTrans; }
#endif
    }

    /** @addtogroup linalg
      @{ */

    /** @defgroup ls Least-squares & Moore-Penrose pseudo-inverse
      @{ */


    //! solve over-determined least-squares problem Mx = b
    template <typename ValueType> 
      inline Vector<ValueType>& solve_LS (Vector<ValueType>& x, const Matrix<ValueType>& M, const Vector<ValueType>& b, Matrix<ValueType>& work)
    {
      work.allocate (M.columns(), M.columns());
      rankN_update (work, M, __transpose<ValueType>(), CblasLower);
      Cholesky::decomp (work);
      mult (x, ValueType (1.0), __transpose<ValueType>(), M, b);
      return Cholesky::solve (x, work);
    }



    //! solve regularised least-squares problem |Mx-b|^2 + r|x|^2 
    template <typename ValueType> 
      inline Vector<ValueType>& solve_LS_reg (
          Vector<ValueType>& x,
          const Matrix<ValueType>& M,
          const Vector<ValueType>& b,
          double reg_weight, 
          Matrix<ValueType>& work)
    {
      work.allocate (M.columns(), M.columns());
      rankN_update (work, M, __transpose<ValueType>(), CblasLower);
      work.diagonal() += ValueType (reg_weight);
      Cholesky::decomp (work);
      mult (x, ValueType (1.0), __transpose<ValueType>(), M, b);
      return Cholesky::solve (x, work);
    }

    //! solve regularised least-squares problem |Mx-b|^2 + |diag(w).x|^2 
    template <typename ValueType, typename RealValueType> 
      inline Vector<ValueType>& solve_LS_reg (
          Vector<ValueType>& x,
          const Matrix<ValueType>& M,
          const Vector<ValueType>& b,
          const Vector<RealValueType>& weights,
          Matrix<ValueType>& work)
    {
      work.allocate (M.columns(), M.columns());
      rankN_update (work, M, __transpose<ValueType>(), CblasLower);
      work.diagonal() += weights;
      Cholesky::decomp (work);
      mult (x, ValueType (1.0), __transpose<ValueType>(), M, b);
      return Cholesky::solve (x, work);
    }



    //! compute Moore-Penrose pseudo-inverse of M given its transpose Mt
    template <typename ValueType> 
      inline Matrix<ValueType>& pinv (Matrix<ValueType>& I, const Matrix<ValueType>& Mt, Matrix<ValueType>& work)
      {
        CBLAS_TRANSPOSE trans1 = Mt.rows() > Mt.columns() ? __transpose<ValueType>() : CblasNoTrans;
        CBLAS_TRANSPOSE trans2 = Mt.rows() > Mt.columns() ? CblasNoTrans : __transpose<ValueType>();
        CBLAS_SIDE side = Mt.rows() > Mt.columns() ? CblasRight : CblasLeft;
        mult (work, ValueType (0.0), ValueType (1.0), trans1, Mt, trans2, Mt);
        Cholesky::inv (work);
        return mult (I, side, ValueType (0.0), ValueType (1.0), CblasUpper, work, Mt);
      }




    //! compute Moore-Penrose pseudo-inverse of M
    template <typename ValueType> 
      inline Matrix<ValueType>& pinv (Matrix<ValueType>& I, const Matrix<ValueType>& M)
      {
      I.allocate (M.columns(), M.rows());
      size_t n = std::min (M.rows(), M.columns());
      Matrix<ValueType> work (n,n);
      Matrix<ValueType> Mt (M.columns(), M.rows());
      return pinv (I, transpose (Mt, M), work);
    }


    //! return Moore-Penrose pseudo-inverse of M
    template <typename ValueType>
      Math::Matrix<ValueType> pinv (const Math::Matrix<ValueType>& M)
      {
        Matrix<ValueType> I;
        pinv (I, M);
        return I;
      }

    /** @} */
    /** @} */








  }
}

#endif








