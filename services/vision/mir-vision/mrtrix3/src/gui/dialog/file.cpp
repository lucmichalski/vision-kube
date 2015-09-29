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

#include <QMessageBox>

#include "app.h"
#include "gui/dialog/file.h"
#include "image/format/list.h"

#ifdef MRTRIX_MACOSX
# define FILE_DIALOG_OPTIONS QFileDialog::DontUseNativeDialog
#else 
# define FILE_DIALOG_OPTIONS static_cast<QFileDialog::Options> (0)
#endif

namespace MR
{
  namespace GUI
  {
    namespace Dialog
    {
      namespace File 
      {
      
        const std::string image_filter_string = "Medical Images (*" + join (MR::Image::Format::known_extensions, " *") + ")";




        std::string get_folder ( QWidget* parent, const std::string& caption, const std::string& folder) 
        {
          QString qstring = QFileDialog::getExistingDirectory (parent, caption.c_str(), folder.size() ? QString(folder.c_str()) : QString(), QFileDialog::ShowDirsOnly | FILE_DIALOG_OPTIONS);
          if (qstring.size()) {
            std::string folder = qstring.toUtf8().data();
            QDir::setCurrent (Path::dirname (folder).c_str());
            return folder;
          }
          return std::string();
        }




        std::string get_file (QWidget* parent, const std::string& caption, const std::string& filter, const std::string& folder)
        {
          QString qstring = QFileDialog::getOpenFileName (parent, caption.c_str(), folder.size() ? QString(folder.c_str()) : QString(), filter.c_str(), 0, FILE_DIALOG_OPTIONS);
          if (qstring.size()) {
            std::string name = qstring.toUtf8().data();
            QDir::setCurrent (Path::dirname (name).c_str());
            return name;
          }
          return std::string();
        }





        std::vector<std::string> get_files (QWidget* parent, const std::string& caption, const std::string& filter, const std::string& folder)
        {
          QStringList qlist = QFileDialog::getOpenFileNames (parent, caption.c_str(), folder.size() ? QString(folder.c_str()) : QString(), filter.c_str(), 0, FILE_DIALOG_OPTIONS);
          std::vector<std::string> list;
          if (qlist.size()) {
            for (int n = 0; n < qlist.size(); ++n) 
              list.push_back (qlist[n].toUtf8().data());
            QDir::setCurrent (Path::dirname (list[0]).c_str());
          }
          return list;
        }


        bool overwrite_files = false;

        void check_overwrite_files_func (const std::string& name) 
        {
          if (overwrite_files)
            return;

          QMessageBox::StandardButton response = QMessageBox::warning (QApplication::activeWindow(), 
              "confirm file overwrite", ("Action will overwrite file \"" + name + "\" - proceed?").c_str(), 
                QMessageBox::Yes | QMessageBox::YesToAll | QMessageBox::Cancel, QMessageBox::Cancel);
          if (response == QMessageBox::Cancel)
            throw Exception ("File overwrite cancelled by user request");
          if (response == QMessageBox::YesToAll)
            overwrite_files = true;
        }



        std::string get_save_name (QWidget* parent, const std::string& caption, const std::string& suggested_name, const std::string& filter, const std::string& folder)
        {
          overwrite_files = false;

          QString selection;
          if (folder.size()) {
            if (suggested_name.size())
              selection = MR::Path::join (folder, suggested_name).c_str();
            else 
              selection = folder.c_str();
          }
          else if (suggested_name.size())
            selection = suggested_name.c_str();
          QString qstring = QFileDialog::getSaveFileName (parent, caption.c_str(), selection, 
              filter.c_str(), 0, FILE_DIALOG_OPTIONS | QFileDialog::DontConfirmOverwrite);
          std::string name;
          if (qstring.size()) {
            name = qstring.toUtf8().data();
            QDir::setCurrent (Path::dirname (name).c_str());
          }
          return name;
        }

      }
    }
  }
}


