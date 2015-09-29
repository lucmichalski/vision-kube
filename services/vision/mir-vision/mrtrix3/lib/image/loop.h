/*
   Copyright 2009 Brain Research Institute, Melbourne, Australia

   Written by J-Donald Tournier, 16/10/09.

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

#ifndef __image_loop_h__
#define __image_loop_h__

#include "apply.h"
#include "progressbar.h"
#include "image/position.h"
#include "image/stride.h"

namespace MR
{
  namespace Image
  {
    using namespace Image;


    //! \cond skip

    template <class LoopType, typename... VoxelType>
      class LoopIter {
        public:
          LoopIter (const LoopType& loop_type, VoxelType&... voxels) : 
            loop (loop_type), vox (voxels...) { 
              unpack ([this](VoxelType&... v) { loop.start (v...); }, vox); 
            }
          LoopIter (LoopIter&&) = default;

          LoopIter () = delete;
          LoopIter (const LoopIter&) = delete;
          LoopIter& operator=(const LoopIter&) = delete;

          operator bool() const { return loop.ok(); }
          void operator++ () { unpack ([this](VoxelType&... v) { loop.next (v...); }, vox); }
          void operator++ (int) { unpack ([this](VoxelType&... v) { loop.next (v...); }, vox); }
        private:
          LoopType loop;
          std::tuple<VoxelType&...> vox;
      };


    namespace {

      struct set_pos {
        set_pos (size_t axis, ssize_t index) : axis (axis), index (index) { }
        template <class VoxelType> 
          void operator() (VoxelType& vox) { vox[axis] = index; }
        size_t axis;
        ssize_t index;
      };

      struct inc_pos {
        inc_pos (size_t axis) : axis (axis) { }
        template <class VoxelType> 
          void operator() (VoxelType& vox) { vox[axis]++; }
        size_t axis;
      };

    }

    //! \endcond

    /** \defgroup loop Looping functions
      @{ */

    //! a class to loop over arbitrary numbers of axes of a VoxelType  
    /*! This class can be used to loop over any number of axes of one of more
     * VoxelType classes, within the same thread of execution (for
     * multi-threaded applications, see Image::ThreadedLoop). Its use is best
     * illustrated with the following examples.
     *
     * If \a vox in the following example is a 3D VoxelType (i.e. vox.ndim() ==
     * 3), then:
     * \code
     * float sum = 0.0;
     * Image::Loop loop;
     * for (auto i = loop (vox); i; ++i)
     *   sum += vox.value();
     * \endcode
     * is equivalent to:
     * \code
     * float sum = 0.0;
     * for (vox[2] = 0; vox[2] < vox.dim(2); ++vox[2])
     *   for (vox[1] = 0; vox[1] < vox.dim(1); ++vox[1])
     *     for (vox[0] = 0; vox[0] < vox.dim(0); ++vox[0])
     *       sum += vox.value();
     * \endcode
     * This has the advantage that the dimensionality of the VoxelType does not
     * need to be known at compile-time. In other words, if the VoxelType was
     * 4-dimensional, the first looping construct would correctly iterate over
     * all voxels, whereas the second one would only process the first 3D
     * volume.
     *
     * \section multiloop Looping over multiple VoxelType objects
     *
     * It is often required to loop over more than one VoxelType of the same
     * dimensions. This is done trivially by passing any additional VoxelType
     * objects to be looped over to the operator() member functions. For example,
     * this code snippet will copy the contents of the VoxelType \a src into a
     * VoxelType \a dest, assumed to have the same dimensions as \a src:
     * \code
     * for (auto i = Image::Loop()(dest, src); i; ++i)
     *   dest.value() = vox.value();
     * \endcode
     *
     * \section restrictedloop Looping over a specific range of axes
     * It is also possible to explicitly specify the range of axes to be looped
     * over. In the following example, the program will loop over each 3D
     * volume in the VoxelType in turn:
     * \code
     * Image::Loop outer (3); // outer loop iterates over axes 3 and above
     * for (auto i = outer (vox)); i; ++i {
     *   float sum = 0.0;
     *   Image::Loop inner (0, 3); // inner loop iterates over axes 0 to 2
     *   for (auto j = inner (vox); j; ++j)
     *     sum += vox.value();
     *   print ("total = " + str (sum) + "\n");
     * }
     * \endcode
     *
     * \section progressloop Displaying progress status
     *
     * The Loop object can also display its progress as it proceeds, using the
     * appropriate constructor. In the following example, the program will
     * display its progress as it averages a VoxelType:
     * \code
     * float sum = 0.0;
     *
     * Loop loop ("averaging...");
     * for (auto i = loop(vox); i; ++i) 
     *   sum += vox.value();
     *
     * float average = sum / float (Image::voxel_count (vox));
     * print ("average = " + str (average) + "\n");
     * \endcode
     * The output would look something like this:
     * \code
     * myprogram: averaging... 100%
     * average = 23.42
     * \endcode
     *
     * \sa Image::LoopInOrder
     * \sa Image::ThreadedLoop
     */
      class Loop
      {
        public:

          //! Constructor
          /*! Construct a Loop object to iterate over the axes specified. By
           * default, the Loop will iterate over all axes of the first VoxelType
           * supplied to next(). If \a from_axis and \a to_axis are specified,
           * the Loop will iterate from axis \a from_axis up to but \b not
           * including axis \a to_axis. */
          Loop (size_t from_axis = 0, size_t to_axis = std::numeric_limits<size_t>::max()) :
            from_ (from_axis), to_ (to_axis), max_axis_ (0), cont_ (true) { }

          //! Constructor with progress status
          /*! Construct a Loop object to iterate over the axes specified and
           * display the progress status with the specified message. By default,
           * the Loop will iterate over all axes of the first VoxelType supplied to
           * next(). If \a from_axis and \a to_axis are specified, the Loop will
           * iterate from axis \a from_axis up to but \b not including axis \a
           * to_axis. */
          Loop (const std::string& message, size_t from_axis = 0, size_t to_axis = std::numeric_limits<size_t>::max()) :
            from_ (from_axis), to_ (to_axis), max_axis_ (0), cont_ (true), progress_ (message, 1) { }

          //! return iteratable object for use in loop
          /*! This start the loop by resetting the appropriate coordinates of
           * each of the specified VoxelType objects to zero, and initialising
           * the progress status if appropriate. Note that only those axes
           * specified in the Loop constructor will have their coordinates set
           * to zero; the coordinates of all other axes will be left untouched.
           *
           * The object returned by this function is designed to be used
           * directly the loop, for example:
           * \code
           * Loop loop ("copy...");
           * for (auto i = loop (vox_in, vox_out); i; ++i)
           *   vox_out.value() = vox_in.value();
           * \endcode */
          template <typename... VoxelType> 
            LoopIter<Loop,VoxelType&...> operator() (VoxelType&... vox) {
              return { *this, vox... };
            }

          //! Start the loop to iterate over the VoxelTypes specified
          /*! Start the loop by resetting the appropriate coordinates of each of
           * the specified VoxelType objects to zero, and initialising the progress status
           * if appropriate. Note that only those axes specified in the Loop
           * constructor will have their coordinates set to zero; the coordinates
           * of all other axes will be left untouched. */
          template <typename... VoxelType>
            void start (VoxelType&... vox) {
              cont_ = true;
              auto& ref = std::get<0> (std::tie (vox...));
              max_axis_ = std::min (ref.ndim(), to_);
              for (size_t n = from_; n < max_axis_; ++n) 
                apply (set_pos (n, 0), std::tie (vox...));

              if (progress_)
                progress_.set_max (voxel_count (ref, from_, to_));
            }

          //! Check whether the loop should continue iterating
          /*! \return true if the loop has not completed, false otherwise. */
          bool ok() const {
            return cont_;
          }

          //! Advance coordinates of specified VoxelTypes
          /*! Advance coordinates of all specified VoxelType objects to the next position
           * to be processed, and update the progress status if appropriate. */
          template <typename... VoxelType>
            void next (VoxelType&... vox) {
              auto tvox = std::tie (vox...);
              ++progress_;
              next_axis (from_, tvox);
            }

          //! set position along relevant axes of \a target to that of \a reference
          /*! set the position of \a target along those axes involved in the
           * loop to the that of \a reference, leaving all other coordinates
           * unchanged. */
          template <typename RefVoxelType, typename... VoxelType>
            void set_position (const RefVoxelType& reference, VoxelType&... target) const {
              auto t = std::tie (target...);
              set_position (reference, t);
            }

          //! set position along relevant axes of \a target to that of \a reference
          /*! set the position of \a target along those axes involved in the
           * loop to the that of \a reference, leaving all other coordinates
           * unchanged. */
          template <typename RefVoxelType, typename... VoxelType>
            void set_position (const RefVoxelType& reference, std::tuple<VoxelType&...>& target) const {
              for (size_t i = from_; i < max_axis_; ++i) 
                apply (set_pos (i, reference[i]), target);
            }


        private:
          const size_t from_, to_;
          size_t max_axis_;
          bool cont_;
          ProgressBar progress_;

          template <typename... VoxelType>
            void next_axis (size_t axis, std::tuple<VoxelType&...>& vox) {
              if (axis < max_axis_) {
                if (std::get<0>(vox)[axis] + 1 < std::get<0>(vox).dim (axis)) 
                  apply (inc_pos (axis), vox);
                else {
                  if (axis+1 == max_axis_) {
                    cont_ = false;
                    progress_.done();
                  }
                  else {
                    next_axis (axis+1, vox);
                    if (cont_) 
                      apply (set_pos (axis, 0), vox);
                  }
                }
              }
              else {
                cont_ = false;
                progress_.done();
              }
            }
      };








    //! a class to loop over arbitrary numbers and orders of axes of a VoxelType
    /*! This class can be used to loop over any number of axes of one of more
     * VoxelType, in any specified order, within the same thread of execution
     * (for multi-threaded applications, see Image::ThreadedLoop). Its use is
     * essentially identical to that of the Loop class, with the difference
     * that axes can now be iterated over in any arbitrary order. This is best
     * illustrated with the following examples.
     *
     * \section strideorderloop Looping with smallest stride first
     * The looping strategy most likely to make most efficient use of the
     * memory infrastructure is one where the innermost loop iterates over the
     * axis with the smallest absolute stride, since voxels along this axis are
     * most likely to be adjacent. This is most likely to optimise both
     * throughput to and from system RAM or disk (which are typically optimised
     * for bursts of contiguous sections of memory), and CPU cache usage.
     *
     * The LoopInOrder class is designed to facilitate this. In the following
     * example, the VoxelType of interest is passed as an argument to the
     * constructor, so that its strides can be used to compute the nesting
     * order for the loops over the corresponding axes. Here, we assume that
     * \a vox is a 3D VoxelType (i.e. vox.ndim() == 3) with strides [ 2 -1 3 ]:
     * \code
     * float sum = 0.0;
     * for (auto i = Image::LoopInOrder() (vox); i ++i)
     *   sum += vox.value();
     * \endcode
     * This is equivalent to:
     * \code
     * float sum = 0.0;
     * for (vox[2] = 0; vox[2] < vox.dim(2); ++vox[2])
     *   for (vox[0] = 0; vox[0] < vox.dim(0); ++vox[0])
     *     for (vox[1] = 0; vox[1] < vox.dim(1); ++vox[1])
     *       sum += vox.value();
     * \endcode
     *
     * \section restrictedorderloop Looping over a specific range of axes
     * It is also possible to explicitly specify the range of axes to be looped
     * over. In the following example, the program will loop over each 3D
     * volume in the VoxelType in turn using the Loop class, and use the
     * LoopInOrder class to iterate over the axes of each volume to ensure
     * efficient memory bandwidth use when each volume is being processed.
     * \code
     * // define inner loop to iterate over axes 0 to 2
     * LoopInOrder inner (vox, 0, 3);
     *
     * // outer loop iterates over axes 3 and above:
     * for (auto i = Loop(3) (vox); i; ++i) {
     *   float sum = 0.0;
     *   for (auto j = inner (vox); j; ++j) {
     *     sum += vox.value();
     *   print ("total = " + str (sum) + "\n");
     * }
     * \endcode
     *
     * \section arbitraryorderloop Arbitrary order loop
     * It is also possible to specify the looping order explictly, as in the
     * following example:
     * \code
     * float value = 0.0;
     * std::vector<size_t> order = { 1, 0, 2 };
     *
     * LoopInOrder loop (vox, order);
     * for (auto i = loop (vox); i; ++i) 
     *   value += std::exp (-vox.value());
     * \endcode
     * This will iterate over the axes in the same order as the first example
     * above, irrespective of the strides of the VoxelType.
     *
     * \section multiorderloop Looping over multiple VoxelType objects:
     *
     * As with the Loop class, it is possible to loop over more than one
     * VoxelType of the same dimensions, by passing any additional VoxelType
     * objects to be looped over to the operator() member function. For example,
     * this code snippet will copy the contents of the VoxelType \a src into a
     * VoxelType \a dest (assumed to have the same dimensions as \a src),
     * with the looping order optimised for the \a src VoxelType:
     * \code
     * LoopInOrder loop (src);
     * for (auto i = loop (src, dest); i; ++i) 
     *   dest.value() = src.value();
     * \endcode
     *
     * \section progressloopinroder Displaying progress status
     * As in the Loop class, the LoopInOrder object can also display its
     * progress as it proceeds, using the appropriate constructor. In the
     * following example, the program will display its progress as it averages
     * a VoxelType:
     * \code
     * float sum = 0.0;
     *
     * LoopInOrder loop (vox, "averaging...");
     * for (auto i = loop (vox); i; ++i)
     *   sum += vox.value();
     *
     * float average = sum / float (Image::voxel_count (vox));
     * print ("average = " + str (average) + "\n");
     * \endcode
     * The output would look something like this:
     * \code
     * myprogram: averaging... 100%
     * average = 23.42
     * \endcode
     *
     * \sa Image::Loop
     * \sa Image::ThreadedLoop
     */
      class LoopInOrder
      {
        public:
          //! Constructor from axes indices
          /*! Construct a LoopInOrder object to iterate over the axes specified. */
          LoopInOrder (const std::vector<size_t>& axes) :
            axes_ (axes), first_axis (axes_[0]), cont_ (true) { }

          //! Construct from axes indices with progress status
          /*! Construct a LoopInOrder object to iterate over the axes specified and
           * display the progress status with the specified message. */
          LoopInOrder (const std::vector<size_t>& axes, const std::string& message) :
            axes_ (axes), first_axis (axes_[0]), cont_ (true), progress_ (message, 1) { }

          //! Construct from VoxelType strides
          /*! Construct a LoopInOrder object to iterate over the axes of \a set
           * in order of smallest stride first. If supplied, the optional
           * arguments \a from_axis and \a to_axis can be used to restrict those
           * axes that will be looped over: the Loop will then iterate from axis
           * \a from_axis up to but \b not including axis \a to_axis. */
          template <class VoxelType>
            LoopInOrder (const VoxelType& vox, size_t from_axis = 0, size_t to_axis = std::numeric_limits<size_t>::max()) :
              axes_ (Stride::order (vox, from_axis, to_axis)), first_axis (axes_[0]), cont_ (true) { }

          //! Constructor from VoxelType strides with progress status
          /*! Construct a LoopInOrder object to iterate over the axes specified
           * in order of smallest stride first, and display the progress status
           * with the specified message. If supplied, the optional arguments \a
           * from_axis and \a to_axis can be used to restrict those axes that
           * will be looped over: the Loop will then iterate from axis \a
           * from_axis up to but \b not including axis \a to_axis. */
          template <class VoxelType>
            LoopInOrder (const VoxelType& vox, const std::string& message, 
                size_t from_axis = 0, size_t to_axis = std::numeric_limits<size_t>::max()) :
              axes_ (Stride::order (vox, from_axis, to_axis)), first_axis (axes_[0]), cont_ (true), progress_ (message, 1) { }

          //! return iteratable object for use in loop
          /*! This start the loop by resetting the appropriate coordinates of
           * each of the specified VoxelType objects to zero, and initialising
           * the progress status if appropriate. Note that only those axes
           * specified in the Loop constructor will have their coordinates set
           * to zero; the coordinates of all other axes will be left untouched.
           *
           * The object returned by this function is designed to be used
           * directly the loop, for example:
           * \code
           * LoopInOrder loop (vox_in, "copy...");
           * for (auto i = loop (vox_in, vox_out); i; ++i)
           *   vox_out.value() = vox_in.value();
           * \endcode */
          template <typename... VoxelType> 
            LoopIter<LoopInOrder,VoxelType&...> operator() (VoxelType&... vox) {
              return { *this, vox... };
            }


          //! Start the loop to iterate over the VoxelTypes specified
          /*! Start the loop by resetting the appropriate coordinates of each of
           * the specified VoxelType objects to zero, and initialising the progress status
           * if appropriate. Note that only those axes specified in the Loop
           * constructor will have their coordinates set to zero; the coordinates
           * of all other axes will be left untouched. */
          template <typename... VoxelType>
            void start (VoxelType&... vox) {
              cont_ = true;
              auto& ref = std::get<0> (std::tie (vox...));
              first_axis_dim = ref.dim (first_axis) - 1;
              for (size_t n = 0; n < axes_.size(); ++n) 
                apply (set_pos (axes_[n], 0), std::tie (vox...));
              if (progress_)
                progress_.set_max (voxel_count (ref, axes_));
            }

          //! Check whether the loop should continue iterating
          /*! \return true if the loop has not completed, false otherwise. */
          bool ok() const {
            return cont_;
          }

          //! Advance coordinates of specified VoxelTypes
          /*! Advance coordinates of all specified VoxelType objects to the next position
           * to be processed, and update the progress status if appropriate. */
          template <typename... VoxelType>
            void next (VoxelType&... vox) {
              auto tvox = std::tie (vox...);
              auto& ref = std::get<0> (tvox);
              ++progress_;
              if (ref[first_axis] < first_axis_dim)
                apply (inc_pos (first_axis), tvox);
              else {
                next_axis (1, tvox);
                if (cont_) 
                  apply (set_pos (first_axis, 0), tvox);
              }
            }

          //! set position along relevant axes of \a target to that of \a reference
          /*! set the position of \a target along those axes involved in the
           * loop to the that of \a reference, leaving all other coordinates
           * unchanged. */
          template <typename RefVoxelType, typename... VoxelType>
            void set_position (const RefVoxelType& reference, VoxelType&... target) const {
              auto t = std::tie (target...);
              set_position (reference, t);
            }

          //! set position along relevant axes of \a target to that of \a reference
          /*! set the position of \a target along those axes involved in the
           * loop to the that of \a reference, leaving all other coordinates
           * unchanged. */
          template <typename RefVoxelType, typename... VoxelType>
            void set_position (const RefVoxelType& reference, std::tuple<VoxelType&...>& target) const {
              for (size_t i = 0; i < axes_.size(); ++i) 
                apply (set_pos (axes_[i], reference[axes_[i]]), target);
            }


          const std::vector<size_t>& axes () const { return axes_; }

        private:
          const std::vector<size_t> axes_;
          const size_t first_axis;
          ssize_t first_axis_dim;
          bool cont_;
          ProgressBar progress_;

          template <typename... VoxelType>
            void next_axis (size_t axis, std::tuple<VoxelType&...>& vox) {
              if (axis == axes_.size()) {
                cont_ = false;
                progress_.done();
                return;
              }

              size_t a = axes_[axis];
              if (std::get<0>(vox)[a] < std::get<0>(vox).dim (a) - 1) 
                apply (inc_pos (a), vox);
              else {
                next_axis (axis+1, vox);
                if (cont_) apply (set_pos (a, 0), vox);
              }
            }

      };


    //! @}
  }
  }

#endif


