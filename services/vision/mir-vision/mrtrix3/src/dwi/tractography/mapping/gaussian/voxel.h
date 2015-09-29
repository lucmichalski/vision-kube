/*
    Copyright 2011 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith, 2014.

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

#ifndef __dwi_tractography_mapping_gaussian_voxel_h__
#define __dwi_tractography_mapping_gaussian_voxel_h__



namespace MR {
namespace DWI {
namespace Tractography {
namespace Mapping {
namespace Gaussian {




// Base class to handle case where the factor contributed by the streamline varies along its length
//   (currently only occurs when the track-wise statistic is Gaussian)
class VoxelAddon
{
  public:
    VoxelAddon() : sum_factors (0.0) { }
    VoxelAddon (const float v) : sum_factors (v) { }
    float get_factor() const { return sum_factors; }
  protected:
    void operator+= (const float f) const { sum_factors += f; }
    void operator=  (const float f) { sum_factors = f; }
    void operator=  (const VoxelAddon& that) { sum_factors = that.sum_factors; }
    void normalise (const float l) const { sum_factors /= l; }
  private:
    mutable float sum_factors;
};




class Voxel : public Mapping::Voxel, public VoxelAddon
{
    typedef Mapping::Voxel Base;
  public:
    Voxel (const int x, const int y, const int z) : Base (x, y, z) , VoxelAddon () { }
    Voxel (const Point<int>& that) : Base (that), VoxelAddon() { }
    Voxel (const Point<int>& v, const float l) : Base (v, l), VoxelAddon () { }
    Voxel (const Point<int>& v, const float l, const float f) : Base (v, l), VoxelAddon (f) { }
    Voxel () : Base (), VoxelAddon () { }

    Voxel& operator= (const Voxel& V) { Base::operator= (V); VoxelAddon::operator= (V); return *this; }
    void operator+= (const float l) const { Base::operator+= (l); };
    bool operator== (const Voxel& V) const { return Base::operator== (V); }
    bool operator<  (const Voxel& V) const { return Base::operator<  (V); }

    void add (const float l, const float f) const { Base::operator+= (l); VoxelAddon::operator+= (f); }
    void normalise() const { VoxelAddon::normalise (get_length()); Base::normalise(); }

};


class VoxelDEC : public Mapping::VoxelDEC, public VoxelAddon
{
    typedef Mapping::VoxelDEC Base;
  public:
    VoxelDEC () : Base (), VoxelAddon () { }
    VoxelDEC (const Point<int>& V) : Base (V), VoxelAddon () { }
    VoxelDEC (const Point<int>& V, const Point<float>& d) : Base (V, d), VoxelAddon () { }
    VoxelDEC (const Point<int>& V, const Point<float>& d, const float l) : Base (V, d, l), VoxelAddon () { }
    VoxelDEC (const Point<int>& V, const Point<float>& d, const float l, const float f) : Base (V, d, l), VoxelAddon (f) { }

    VoxelDEC& operator=  (const VoxelDEC& V)   { Base::operator= (V); VoxelAddon::operator= (V); return (*this); }
    void operator+= (const float) const { assert (0); }
    void operator+= (const Point<float>&) const { assert (0); };
    bool operator== (const VoxelDEC& V) const { return Base::operator== (V); }
    bool operator<  (const VoxelDEC& V) const { return Base::operator<  (V); }

    void add (const Point<float>&, const float) const { assert (0); };
    void add (const Point<float>& i, const float l, const float f) const { Base::add (i, l); VoxelAddon::operator+= (f); }
    void normalise() const { VoxelAddon::normalise (get_length()); Base::normalise(); }

};


class Dixel : public Mapping::Dixel, public VoxelAddon
{
    typedef Mapping::Dixel Base;
  public:
    Dixel () : Base (), VoxelAddon () { }
    Dixel (const Point<int>& V) : Base (V), VoxelAddon () { }
    Dixel (const Point<int>& V, const size_t b) : Base (V, b), VoxelAddon () { }
    Dixel (const Point<int>& V, const size_t b, const float l) : Base (V, b, l), VoxelAddon () { }
    Dixel (const Point<int>& V, const size_t b, const float l, const float f) : Base (V, b, l), VoxelAddon (f) { }

    Dixel& operator=  (const Dixel& V)       { Base::operator= (V); VoxelAddon::operator= (V); return *this; }
    bool   operator== (const Dixel& V) const { return Base::operator== (V); }
    bool   operator<  (const Dixel& V) const { return Base::operator<  (V); }
    void   operator+= (const float)  const { assert (0); };

    void add (const float l, const float f) const { Base::operator+= (l); VoxelAddon::operator+= (f); }
    void normalise() const { VoxelAddon::normalise (get_length()); Base::normalise(); }

};


class VoxelTOD : public Mapping::VoxelTOD, public VoxelAddon
{
    typedef Mapping::VoxelTOD Base;
  public:
    VoxelTOD () : Base (), VoxelAddon () { }
    VoxelTOD (const Point<int>& V) : Base (V), VoxelAddon () { }
    VoxelTOD (const Point<int>& V, const Math::Vector<float>& t) : Base (V, t), VoxelAddon () { }
    VoxelTOD (const Point<int>& V, const Math::Vector<float>& t, const float l) : Base (V, t, l), VoxelAddon () { }
    VoxelTOD (const Point<int>& V, const Math::Vector<float>& t, const float l, const float f) : Base (V, t, l), VoxelAddon (f) { }

    VoxelTOD& operator=  (const VoxelTOD& V)   { Base::operator= (V); VoxelAddon::operator= (V); return (*this); }
    bool      operator== (const VoxelTOD& V) const { return Base::operator== (V); }
    bool      operator<  (const VoxelTOD& V) const { return Base::operator< (V); }
    void      operator+= (const Math::Vector<float>&) const { assert (0); };

    void add (const Math::Vector<float>&, const float) const { assert (0); };
    void add (const Math::Vector<float>& i, const float l, const float f) const { Base::add (i, l); VoxelAddon::operator+= (f); }
    void normalise() const { VoxelAddon::normalise (get_length()); Base::normalise(); }

};






// Unlike standard TWI, with Gaussian smoothing the TWI factor is stored per point rather than per streamline
// However, it's handy from a code perspective to still use the same base class
/*
class SetVoxelExtras
{
  public:
    size_t index;
    float weight;
};
*/





class SetVoxel : public std::set<Voxel>, public Mapping::SetVoxelExtras
{
  public:
    typedef Voxel VoxType;
    inline void insert (const Point<int>& v, const float l, const float f)
    {
      const Voxel temp (v, l, f);
      iterator existing = std::set<Voxel>::find (temp);
      if (existing == std::set<Voxel>::end())
        std::set<Voxel>::insert (temp);
      else
        (*existing).add (l, f);
    }
};
class SetVoxelDEC : public std::set<VoxelDEC>, public Mapping::SetVoxelExtras
{
  public:
    typedef VoxelDEC VoxType;
    inline void insert (const Point<int>& v, const Point<float>& d, const float l, const float f)
    {
      const VoxelDEC temp (v, d, l, f);
      iterator existing = std::set<VoxelDEC>::find (temp);
      if (existing == std::set<VoxelDEC>::end())
        std::set<VoxelDEC>::insert (temp);
      else
        (*existing).add (d, l, f);
    }
};
class SetDixel : public std::set<Dixel>, public Mapping::SetVoxelExtras
{
  public:
    typedef Dixel VoxType;
    inline void insert (const Point<int>& v, const size_t d, const float l, const float f)
    {
      const Dixel temp (v, d, l, f);
      iterator existing = std::set<Dixel>::find (temp);
      if (existing == std::set<Dixel>::end())
        std::set<Dixel>::insert (temp);
      else
        (*existing).add (l, f);
    }
};
class SetVoxelTOD : public std::set<VoxelTOD>, public Mapping::SetVoxelExtras
{
  public:
    typedef VoxelTOD VoxType;
    inline void insert (const Point<int>& v, const Math::Vector<float>& t, const float l, const float f)
    {
      const VoxelTOD temp (v, t, l, f);
      iterator existing = std::set<VoxelTOD>::find (temp);
      if (existing == std::set<VoxelTOD>::end())
        std::set<VoxelTOD>::insert (temp);
      else
        (*existing).add (t, l, f);
    }
};



}
}
}
}
}

#endif



