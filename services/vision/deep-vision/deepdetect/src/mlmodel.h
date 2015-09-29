/**
 * DeepDetect
 * Copyright (c) 2014-2015 Emmanuel Benazera
 * Author: Emmanuel Benazera <beniz@droidnik.fr>
 *
 * This file is part of deepdetect.
 *
 * deepdetect is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * deepdetect is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with deepdetect.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef MODEL_H
#define MODEL_H

/*#ifndef MLMODEL_TEMPLATE_REPO
#define MLMODEL_TEMPLATE_REPO "templates/"
#endif*/

#include <string>

namespace dd
{
  class MLModel
  {
  public:
    MLModel() {};
    MLModel(const std::string &repo)
      :_repo(repo) {}
    ~MLModel() {};

    std::string _repo; /**< model repository. */
    std::string _mlmodel_template_repo = "templates/";
  };
}

#endif
