/*
    Copyright 2011 Brain Research Institute, Melbourne, Australia

    Written by Robert E. Smith, 2011.

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

#ifndef __dwi_tractography_mapping_voxel_h__
#define __dwi_tractography_mapping_voxel_h__



#include <set>

#include "point.h"

#include "image/info.h"


namespace MR {
namespace DWI {
namespace Tractography {
namespace Mapping {



// Helper functions; note that Point<int> rather than Voxel is always used during the mapping itself
inline Point<int> round (const Point<float>& p)
{ 
  assert (std::isfinite (p[0]) && std::isfinite (p[1]) && std::isfinite (p[2]));
  return (Point<int> (std::lround (p[0]), std::lround (p[1]), std::lround (p[2])));
}

inline bool check (const Point<int>& V, const Image::Info& H)
{
  return (V[0] >= 0 && V[0] < H.dim(0) && V[1] >= 0 && V[1] < H.dim(1) && V[2] >= 0 && V[2] < H.dim(2));
}

inline Point<float> vec2DEC (const Point<float>& d)
{
  return (Point<float> (std::abs(d[0]), std::abs(d[1]), std::abs(d[2])));
}




class Voxel : public Point<int>
{
  public:
    Voxel (const int x, const int y, const int z) : length (1.0f) { p[0] = x; p[1] = y; p[2] = z; }
    Voxel (const Point<int>& that) : Point<int> (that), length (1.0f) { }
    Voxel (const Point<int>& v, const float l) : Point<int> (v), length (l) { }
    Voxel () : length (0.0f) { memset (p, 0x00, 3 * sizeof(int)); }
    bool operator< (const Voxel& V) const { return ((p[2] == V.p[2]) ? ((p[1] == V.p[1]) ? (p[0] < V.p[0]) : (p[1] < V.p[1])) : (p[2] < V.p[2])); }
    Voxel& operator= (const Voxel& V) { Point<int>::operator= (V); length = V.length; return *this; }
    void operator+= (const float l) const { length += l; }
    void normalise() const { length = 1.0f; }
    float get_length() const { return length; }
  private:
    mutable float length;
};


class VoxelDEC : public Voxel 
{

  public:
    VoxelDEC () :
        Voxel (),
        colour (Point<float> (0.0f, 0.0f, 0.0f)) { }

    VoxelDEC (const Point<int>& V) :
        Voxel (V),
        colour (Point<float> (0.0f, 0.0f, 0.0f)) { }

    VoxelDEC (const Point<int>& V, const Point<float>& d) :
        Voxel (V),
        colour (vec2DEC (d)) { }

    VoxelDEC (const Point<int>& V, const Point<float>& d, const float l) :
        Voxel (V, l),
        colour (vec2DEC (d)) { }

    VoxelDEC& operator=  (const VoxelDEC& V)   { Voxel::operator= (V); colour = V.colour; return (*this); }
    VoxelDEC& operator=  (const Point<int>& V) { Voxel::operator= (V); colour = Point<float> (0.0f, 0.0f, 0.0f); return (*this); }

    // For sorting / inserting, want to identify the same voxel, even if the colour is different
    bool      operator== (const VoxelDEC& V) const { return Voxel::operator== (V); }
    bool      operator<  (const VoxelDEC& V) const { return Voxel::operator< (V); }

    void normalise() const { colour.normalise(); Voxel::normalise(); }
    void set_dir (const Point<float>& i) { colour = vec2DEC (i); }
    void add (const Point<float>& i, const float l) const { Voxel::operator+= (l); colour += vec2DEC (i); }
    void operator+= (const Point<float>& i) const { Voxel::operator+= (1.0f); colour += vec2DEC (i); }
    const Point<float>& get_colour() const { return colour; }

  private:
    mutable Point<float> colour;

};


// Temporary fix for fixel stats branch
// Stores precise direction through voxel rather than mapping to a DEC colour or a dixel
class VoxelDir : public Voxel
{

  public:
    VoxelDir () :
        Voxel (),
        dir (Point<float> (0.0f, 0.0f, 0.0f)) { }

    VoxelDir (const Point<int>& V) :
        Voxel (V),
        dir (Point<float> (0.0f, 0.0f, 0.0f)) { }

    VoxelDir (const Point<int>& V, const Point<float>& d) :
        Voxel (V),
        dir (d) { }

    VoxelDir (const Point<int>& V, const Point<float>& d, const float l) :
        Voxel (V, l),
        dir (d) { }

    VoxelDir& operator=  (const VoxelDir& V)   { Voxel::operator= (V); dir = V.dir; return (*this); }
    VoxelDir& operator=  (const Point<int>& V) { Voxel::operator= (V); dir = Point<float> (0.0f, 0.0f, 0.0f); return (*this); }

    bool      operator== (const VoxelDir& V) const { return Voxel::operator== (V); }
    bool      operator<  (const VoxelDir& V) const { return Voxel::operator< (V); }

    void normalise() const { dir.normalise(); Voxel::normalise(); }
    void set_dir (const Point<float>& i) { dir = i; }
    void add (const Point<float>& i, const float l) const { Voxel::operator+= (l); dir += i * (dir.dot(i) < 0.0f ? -1.0f : 1.0f); }
    void operator+= (const Point<float>& i) const { Voxel::operator+= (1.0f); dir += i * (dir.dot(i) < 0.0f ? -1.0f : 1.0f); }
    const Point<float>& get_dir() const { return dir; }

  private:
    mutable Point<float> dir;

};



// Assumes tangent has been mapped to a hemisphere basis direction set
class Dixel : public Voxel
{

  public:
    Dixel () :
        Voxel (),
        dir (invalid) { }

    Dixel (const Point<int>& V) :
        Voxel (V),
        dir (invalid) { }

    Dixel (const Point<int>& V, const size_t b) :
        Voxel (V),
        dir (b) { }

    Dixel (const Point<int>& V, const size_t b, const float l) :
        Voxel (V, l),
        dir (b) { }

    void set_dir   (const size_t b) { dir = b; }

    bool   valid()     const { return (dir != invalid); }
    size_t get_dir()   const { return dir; }

    Dixel& operator=  (const Dixel& V)       { Voxel::operator= (V); dir = V.dir; return *this; }
    Dixel& operator=  (const Point<int>& V)  { Voxel::operator= (V); dir = invalid; return *this; }
    bool   operator== (const Dixel& V) const { return (Voxel::operator== (V) ? (dir == V.dir) : false); }
    bool   operator<  (const Dixel& V) const { return (Voxel::operator== (V) ? (dir <  V.dir) : Voxel::operator< (V)); }
    void   operator+= (const float l)  const { Voxel::operator+= (l); }

  private:
    size_t dir;

    static const size_t invalid;

};



// TOD class: tore the SH coefficients in the voxel class so that aPSF generation can be multi-threaded
// Provide a normalise() function to remove any length dependence, and have unary contribution per streamline
class VoxelTOD : public Voxel
{

  public:
    VoxelTOD () :
      Voxel (),
      sh_coefs () { }

    VoxelTOD (const Point<int>& V) :
      Voxel (V),
      sh_coefs () { }

    VoxelTOD (const Point<int>& V, const Math::Vector<float>& t) :
      Voxel (V),
      sh_coefs (t) { }

    VoxelTOD (const Point<int>& V, const Math::Vector<float>& t, const float l) :
      Voxel (V, l),
      sh_coefs (t) { }

    VoxelTOD& operator=  (const VoxelTOD& V)   { Voxel::operator= (V); sh_coefs = V.sh_coefs; return (*this); }
    VoxelTOD& operator=  (const Point<int>& V) { Voxel::operator= (V); sh_coefs.clear(); return (*this); }

    // For sorting / inserting, want to identify the same voxel, even if the TOD is different
    bool      operator== (const VoxelTOD& V) const { return Voxel::operator== (V); }
    bool      operator<  (const VoxelTOD& V) const { return Voxel::operator< (V); }

    void normalise() const
    {
      const float multiplier = 1.0f / get_length();
      sh_coefs *= multiplier;
      Voxel::normalise();
    }
    void set_tod (const Math::Vector<float>& i) { sh_coefs = i; }
    void add (const Math::Vector<float>& i, const float l) const
    {
      assert (i.size() == sh_coefs.size());
      for (size_t index = 0; index != sh_coefs.size(); ++index)
        sh_coefs[index] += l * i[index];
      Voxel::operator+= (l);
    }
    void operator+= (const Math::Vector<float>& i) const
    {
      assert (i.size() == sh_coefs.size());
      sh_coefs += i;
      Voxel::operator+= (1.0f);
    }
    const Math::Vector<float>& get_tod() const { return sh_coefs; }

  private:
    mutable Math::Vector<float> sh_coefs;

};







class SetVoxelExtras
{
  public:
    float factor; // For TWI, when contribution to the map is uniform along the length of the track
    size_t index; // Index of the track
    float weight; // Cross-sectional multiplier for the track
};






// Set classes that give sensible behaviour to the insert() function depending on the base voxel class

class SetVoxel : public std::set<Voxel>, public SetVoxelExtras
{
  public:
    typedef Voxel VoxType;
    inline void insert (const Voxel& v)
    {
      iterator existing = std::set<Voxel>::find (v);
      if (existing == std::set<Voxel>::end())
        std::set<Voxel>::insert (v);
      else
        (*existing) += v.get_length();
    }
    inline void insert (const Point<int>& v, const float l)
    {
      const Voxel temp (v, l);
      insert (temp);
    }
};
class SetVoxelDEC : public std::set<VoxelDEC>, public SetVoxelExtras
{
  public:
    typedef VoxelDEC VoxType;
    inline void insert (const VoxelDEC& v)
    {
      iterator existing = std::set<VoxelDEC>::find (v);
      if (existing == std::set<VoxelDEC>::end())
        std::set<VoxelDEC>::insert (v);
      else
        (*existing).add (v.get_colour(), v.get_length());
    }
    inline void insert (const Point<int>& v, const Point<float>& d)
    {
      const VoxelDEC temp (v, d);
      insert (temp);
    }
    inline void insert (const Point<int>& v, const Point<float>& d, const float l)
    {
      const VoxelDEC temp (v, d, l);
      insert (temp);
    }
};
class SetVoxelDir : public std::set<VoxelDir>, public SetVoxelExtras
{
  public:
    typedef VoxelDir VoxType;
    inline void insert (const VoxelDir& v)
    {
      iterator existing = std::set<VoxelDir>::find (v);
      if (existing == std::set<VoxelDir>::end())
        std::set<VoxelDir>::insert (v);
      else
        (*existing).add (v.get_dir(), v.get_length());
    }
    inline void insert (const Point<int>& v, const Point<float>& d)
    {
      const VoxelDir temp (v, d);
      insert (temp);
    }
    inline void insert (const Point<int>& v, const Point<float>& d, const float l)
    {
      const VoxelDir temp (v, d, l);
      insert (temp);
    }
};
class SetDixel : public std::set<Dixel>, public SetVoxelExtras
{
  public:
    typedef Dixel VoxType;
    inline void insert (const Dixel& v)
    {
      iterator existing = std::set<Dixel>::find (v);
      if (existing == std::set<Dixel>::end())
        std::set<Dixel>::insert (v);
      else
        (*existing) += v.get_length();
    }
    inline void insert (const Point<int>& v, const size_t d)
    {
      const Dixel temp (v, d);
      insert (temp);
    }
    inline void insert (const Point<int>& v, const size_t d, const float l)
    {
      const Dixel temp (v, d, l);
      insert (temp);
    }
};
class SetVoxelTOD : public std::set<VoxelTOD>, public SetVoxelExtras
{
  public:
    typedef VoxelTOD VoxType;
    inline void insert (const VoxelTOD& v)
    {
      iterator existing = std::set<VoxelTOD>::find (v);
      if (existing == std::set<VoxelTOD>::end())
        std::set<VoxelTOD>::insert (v);
      else
        (*existing) += v.get_tod();
    }
    inline void insert (const Point<int>& v, const Math::Vector<float>& t)
    {
      const VoxelTOD temp (v, t);
      insert (temp);
    }
    inline void insert (const Point<int>& v, const Math::Vector<float>& t, const float l)
    {
      const VoxelTOD temp (v, t, l);
      insert (temp);
    }
};



}
}
}
}

#endif



