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

#include "math/vector.h"
#include "gui/color_button.h"
#include "gui/lighting_dock.h"
#include "file/config.h"

namespace MR
{
  namespace GUI
  {
    LightingSettings::LightingSettings (QWidget* parent, GL::Lighting& lighting, bool include_object_color) :
      QFrame (parent),
      info (lighting)
    {
      QColor C;
      QVBoxLayout* main_box = new QVBoxLayout;
      setLayout (main_box);
      QGridLayout* grid_layout = new QGridLayout;
      main_box->addLayout (grid_layout);
      main_box->addStretch ();

      QColorButton* cbutton;
      QSlider* slider;

      QFont f = font();
      f.setPointSize (MR::File::Config::get_int ("MRViewToolFontSize", f.pointSize()-2));
      setFont (f);
      setFrameShadow (QFrame::Sunken);
      setFrameShape (QFrame::Panel);

      if (include_object_color) {
        C.setRgbF (info.object_color[0], info.object_color[1], info.object_color[2]);
        cbutton = new QColorButton (C);
        connect (cbutton, SIGNAL (changed (const QColor&)), this, SLOT (object_color_slot (const QColor&)));
        grid_layout->addWidget (new QLabel ("Object colour"), 0, 0);
        grid_layout->addWidget (cbutton, 0, 1);
      }

      slider = new QSlider (Qt::Horizontal);
      slider->setRange (0,1000);
      slider->setSliderPosition (int (info.ambient * 1000.0));
      connect (slider, SIGNAL (valueChanged (int)), this, SLOT (ambient_intensity_slot (int)));
      grid_layout->addWidget (new QLabel ("Ambient intensity"), 1, 0);
      grid_layout->addWidget (slider, 1, 1);

      slider = new QSlider (Qt::Horizontal);
      slider->setRange (0,1000);
      slider->setSliderPosition (int (info.diffuse * 1000.0));
      connect (slider, SIGNAL (valueChanged (int)), this, SLOT (diffuse_intensity_slot (int)));
      grid_layout->addWidget (new QLabel ("Diffuse intensity"), 2, 0);
      grid_layout->addWidget (slider, 2, 1);

      slider = new QSlider (Qt::Horizontal);
      slider->setRange (0,1000);
      slider->setSliderPosition (int (info.specular * 1000.0));
      connect (slider, SIGNAL (valueChanged (int)), this, SLOT (specular_intensity_slot (int)));
      grid_layout->addWidget (new QLabel ("Specular intensity"), 3, 0);
      grid_layout->addWidget (slider, 3, 1);

      slider = new QSlider (Qt::Horizontal);
      slider->setRange (10,10000);
      slider->setSliderPosition (int (info.shine * 1000.0));
      connect (slider, SIGNAL (valueChanged (int)), this, SLOT (shine_slot (int)));
      grid_layout->addWidget (new QLabel ("Specular exponent"), 4, 0);
      grid_layout->addWidget (slider, 4, 1);

      elevation_slider = new QSlider (Qt::Horizontal);
      elevation_slider->setRange (0,1000);
      elevation_slider->setSliderPosition (int ( (1000.0/Math::pi) *acos (-info.lightpos[1]/Math::norm (info.lightpos))));
      connect (elevation_slider, SIGNAL (valueChanged (int)), this, SLOT (light_position_slot()));
      grid_layout->addWidget (new QLabel ("Light elevation"), 5, 0);
      grid_layout->addWidget (elevation_slider, 5, 1);

      azimuth_slider = new QSlider (Qt::Horizontal);
      azimuth_slider->setRange (-1000,1000);
      azimuth_slider->setSliderPosition (int ( (1000.0/Math::pi) *atan2 (info.lightpos[0], info.lightpos[2])));
      connect (azimuth_slider, SIGNAL (valueChanged (int)), this, SLOT (light_position_slot()));
      grid_layout->addWidget (new QLabel ("Light azimuth"), 6, 0);
      grid_layout->addWidget (azimuth_slider, 6, 1);


      grid_layout->setColumnStretch (0,0);
      grid_layout->setColumnStretch (1,1);
      grid_layout->setColumnMinimumWidth (1, 100);
    }

    void LightingSettings::object_color_slot (const QColor& new_color)
    {
      info.object_color[0] = new_color.redF();
      info.object_color[1] = new_color.greenF();
      info.object_color[2] = new_color.blueF();
      info.update();
    }

    void LightingSettings::ambient_intensity_slot (int value)
    {
      info.ambient = float (value) /1000.0;
      info.update();
    }

    void LightingSettings::diffuse_intensity_slot (int value)
    {
      info.diffuse = float (value) /1000.0;
      info.update();
    }

    void LightingSettings::specular_intensity_slot (int value)
    {
      info.specular = float (value) /1000.0;
      info.update();
    }

    void LightingSettings::shine_slot (int value)
    {
      info.shine = float (value) /1000.0;
      info.update();
    }

    void LightingSettings::light_position_slot ()
    {
      float elevation = elevation_slider->value() * (Math::pi/1000.0);
      float azimuth = azimuth_slider->value() * (Math::pi/1000.0);
      info.lightpos[2] = sin (elevation) * cos (azimuth);
      info.lightpos[0] = sin (elevation) * sin (azimuth);
      info.lightpos[1] = -cos (elevation);
      info.update();
    }

    LightingDock::LightingDock (const std::string& title, GL::Lighting& lighting, bool include_object_color) :
      QDockWidget (QString (title.c_str())),
      settings (new LightingSettings (this, lighting, include_object_color))
    {
      setWidget(settings);
    }
  }
}
