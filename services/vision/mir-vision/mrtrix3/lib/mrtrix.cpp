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

#include <gsl/gsl_version.h>

#include "mrtrix.h"

namespace MR
{


  /************************************************************************
   *                       Miscellaneous functions                        *
   ************************************************************************/

  std::vector<float> parse_floats (const std::string& spec)
  {
    std::vector<float> V;
    if (!spec.size()) throw Exception ("floating-point sequence specifier is empty");
    std::string::size_type start = 0, end;
    float range_spec[3];
    int i = 0;
    try {
      do {
        end = spec.find_first_of (",:", start);
        std::string sub (spec.substr (start, end-start));
        range_spec[i] = (sub.empty() || sub == "nan") ? NAN : to<float> (sub);
        char last_char = end < spec.size() ? spec[end] : '\0';
        if (last_char == ':') {
          i++;
          if (i > 2) throw Exception ("invalid number range in number sequence \"" + spec + "\"");
        } else {
          if (i) {
            if (i != 2)
              throw Exception ("For floating-point ranges, must specify three numbers (start:step:end)");
            float first = range_spec[0], inc = range_spec[1], last = range_spec[2];
            if (!inc || (inc * (last-first) < 0.0) || !std::isfinite(first) || !std::isfinite(inc) || !std::isfinite(last))
              throw Exception ("Floating-point range does not form a finite set");
            float value = first;
            for (size_t mult = 0; (inc>0.0f ? value < last - 0.5f*inc : value > last + 0.5f*inc); ++mult)
              V.push_back ((value = first + (mult * inc)));
          } else {
            V.push_back (range_spec[0]);
          }
          i = 0;
        }
        start = end+1;
      } while (end < spec.size());
    }
    catch (Exception& E) {
      throw Exception (E, "can't parse floating-point sequence specifier \"" + spec + "\"");
    }
    return (V);
  }




  std::vector<int> parse_ints (const std::string& spec, int last)
  {
    std::vector<int> V;
    if (!spec.size()) throw Exception ("integer sequence specifier is empty");
    std::string::size_type start = 0, end;
    int num[3];
    int i = 0;
    try {
      do {
        end = spec.find_first_of (",:", start);
        std::string token (strip (spec.substr (start, end-start)));
        lowercase (token);
        if (token == "end") {
          if (last == std::numeric_limits<int>::max())
            throw Exception ("value of \"end\" is not known in number sequence \"" + spec + "\"");
          num[i] = last;
        }
        else num[i] = to<int> (spec.substr (start, end-start));

        char last_char = end < spec.size() ? spec[end] : '\0';
        if (last_char == ':') {
          i++;
          if (i > 2) throw Exception ("invalid number range in number sequence \"" + spec + "\"");
        }
        else {
          if (i) {
            int inc, last;
            if (i == 2) {
              inc = num[1];
              last = num[2];
            }
            else {
              inc = 1;
              last = num[1];
            }
            if (inc * (last - num[0]) < 0) inc = -inc;
            for (; (inc > 0 ? num[0] <= last : num[0] >= last) ; num[0] += inc) V.push_back (num[0]);
          }
          else V.push_back (num[0]);
          i = 0;
        }

        start = end+1;
      }
      while (end < spec.size());
    }
    catch (Exception& E) {
      throw Exception (E, "can't parse integer sequence specifier \"" + spec + "\"");
    }

    return (V);
  }







  std::vector<std::string> split (const std::string& string, const char* delimiters, bool ignore_empty_fields, size_t num)
  {
    std::vector<std::string> V;
    std::string::size_type start = 0, end;
    try {
      do {
        end = string.find_first_of (delimiters, start);
        V.push_back (string.substr (start, end-start));
        if (end >= string.size()) break;
        start = ignore_empty_fields ? string.find_first_not_of (delimiters, end+1) : end+1;
        if (start > string.size()) break;
        if (V.size()+1 >= num) {
          V.push_back (string.substr (start));
          break;
        }
      } while (true);
    }
    catch (...)  {
      throw Exception ("can't split string \"" + string + "\"");
    }
    return V;
  }



}
