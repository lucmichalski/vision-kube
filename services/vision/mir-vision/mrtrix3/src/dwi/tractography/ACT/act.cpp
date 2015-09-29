#include "dwi/tractography/ACT/act.h"
#include "dwi/tractography/properties.h"


namespace MR
{
  namespace DWI
  {
    namespace Tractography
    {
      namespace ACT
      {

        using namespace App;

        const OptionGroup ACTOption = OptionGroup ("Anatomically-Constrained Tractography options")

          + Option ("act", "use the Anatomically-Constrained Tractography framework during tracking;\n"
                           "provided image must be in the 5TT (five-tissue-type) format")
            + Argument ("image").type_image_in()

          + Option ("backtrack", "allow tracks to be truncated and re-tracked if a poor structural termination is encountered")

          + Option ("crop_at_gmwmi", "crop streamline endpoints more precisely as they cross the GM-WM interface");



        void load_act_properties (Properties& properties)
        {

          using namespace MR::App;

          Options opt = get_options ("act");
          if (opt.size()) {

            properties["act"] = std::string (opt[0][0]);
            opt = get_options ("backtrack");
            if (opt.size())
              properties["backtrack"] = "1";
            opt = get_options ("crop_at_gmwmi");
            if (opt.size())
              properties["crop_at_gmwmi"] = "1";

          } else {

            if (get_options ("backtrack").size())
              WARN ("ignoring -backtrack option - only valid if using ACT");
            if (get_options ("crop_at_gmwmi").size())
              WARN ("ignoring -crop_at_gmwmi option - only valid if using ACT");

          }

        }




        void verify_5TT_image (const Image::Header& H)
        {
          if (!H.datatype().is_floating_point() || H.ndim() != 4 || H.dim(3) != 5)
            throw Exception ("Image " + H.name() + " is not a valid ACT 5TT image");
        }




      }
    }
  }
}


