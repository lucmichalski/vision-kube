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

#ifndef OUTPUTCONNECTORSTRATEGY_H
#define OUTPUTCONNECTORSTRATEGY_H

#include <map>
#include <algorithm>
#include <iostream>
#include <numeric>
#include <Eigen/Dense>

namespace dd
{
  typedef Eigen::MatrixXd dMat;
  typedef Eigen::VectorXd dVec;
  
  /**
   * \brief bad parameter exception
   */
  class OutputConnectorBadParamException : public std::exception
  {
  public:
    OutputConnectorBadParamException(const std::string &s)
      :_s(s) {}
    ~OutputConnectorBadParamException() {}
    const char* what() const noexcept { return _s.c_str(); }
  private:
    std::string _s;
  };

  /**
   * \brief internal error exception
   */
  class OutputConnectorInternalException : public std::exception
  {
  public:
    OutputConnectorInternalException(const std::string &s)
      :_s(s) {}
    ~OutputConnectorInternalException() {}
    const char* what() const noexcept { return _s.c_str(); }
  private:
    std::string _s;
  };
  
  /**
   * \brief main output connector class
   */
  class OutputConnectorStrategy
  {
  public:
    OutputConnectorStrategy() {}
    ~OutputConnectorStrategy() {}

    /**
     * \brief output data reading
     */
    //int transform() { return 1; }
    
    /**
     * \brief initialization of output data connector
     * @param ad data object for "parameters/output"
     */
    void init(const APIData &ad);
  };

  /**
   * \brief no output connector class
   */
  class NoOutputConn : public OutputConnectorStrategy
  {
  public:
    NoOutputConn()
      :OutputConnectorStrategy() {}
    ~NoOutputConn() {}
  };

  /**
   * \brief supervised machine learning output connector class
   */
  class SupervisedOutput : public OutputConnectorStrategy
  {
  public:

    /**
     * \brief supervised result
     */
    class sup_result
    {
    public:
      /**
       * \brief constructor
       * @param loss result loss
       */
      sup_result(const std::string &label, const double &loss=0.0)
	:_label(label),_loss(loss) {}
      
      /**
       * \brief destructor
       */
      ~sup_result() {}

      /**
       * \brief add category to result
       * @param prob category predicted probability
       * @Param cat category name
       */
      inline void add_cat(const double &prob, const std::string &cat)
      {
	_cats.insert(std::pair<double,std::string>(prob,cat));
      }

      std::string _label;
      double _loss = 0.0; /**< result loss. */
      std::map<double,std::string,std::greater<double>> _cats; /**< categories and probabilities for this result */
    };

  public:
    /**
     * \brief supervised output connector constructor
     */
  SupervisedOutput()
      :OutputConnectorStrategy()
      {
      }
    
    /**
     * \brief supervised output connector copy-constructor
     * @param sout supervised output connector
     */
    SupervisedOutput(const SupervisedOutput &sout)
      :OutputConnectorStrategy(),_best(sout._best)
      {
      }
    
    ~SupervisedOutput() {}

    /**
     * \brief supervised output connector initialization
     * @param ad data object for "parameters/output"
     */
    void init(const APIData &ad)
    {
      APIData ad_out = ad.getobj("parameters").getobj("output");
      if (ad_out.has("best"))
	_best = ad_out.get("best").get<int>();
    }

    /**
     * \brief add prediction result to supervised connector output
     * @param uri result uri
     * @param loss result loss
     */
    inline void add_result(const std::string &uri, const double &loss)
    {
      std::unordered_map<std::string,int>::iterator hit;
      if ((hit=_vcats.find(uri))==_vcats.end())
	{
	  _vcats.insert(std::pair<std::string,int>(uri,_vvcats.size()));
	  _vvcats.push_back(sup_result(uri,loss));
	}
      else _vvcats.at((*hit).second)._loss = loss;
    }

    /**
     * \brief add predicted category and probability to existing result
     * @param uri result uri
     * @param prob  category probability
     * @param cat category name
     */
    inline void add_cat(const std::string &uri, const double &prob, const std::string &cat)
    {
      std::unordered_map<std::string,int>::iterator hit;
      if ((hit=_vcats.find(uri))!=_vcats.end())
	{
	  _vvcats.at((*hit).second).add_cat(prob,cat);
	}
      // XXX: else error ?
    }

    /**
     * \brief best categories selection from results
     * @param ad_out output data object
     * @param bcats supervised output connector
     */
    void best_cats(const APIData &ad_out, SupervisedOutput &bcats) const
    {
      int best = _best;
      if (ad_out.has("best"))
	best = ad_out.get("best").get<int>();
      for (size_t i=0;i<_vvcats.size();i++)
	{
	  sup_result sresult = _vvcats.at(i);
	  sup_result bsresult(sresult._label,sresult._loss);
	  std::copy_n(sresult._cats.begin(),std::min(best,static_cast<int>(sresult._cats.size())),
		      std::inserter(bsresult._cats,bsresult._cats.end()));
	  bcats._vcats.insert(std::pair<std::string,int>(sresult._label,bcats._vvcats.size()));
	  bcats._vvcats.push_back(bsresult);
	}
    }

    struct PredictionAndAnswer {
      float prediction;
      unsigned char answer; //this is either 0 or 1
    };

    // measure
    static void measure(const APIData &ad_res, const APIData &ad_out, APIData &out)
    {
      APIData meas_out;
      bool tloss = ad_res.has("train_loss");
      bool loss = ad_res.has("loss");
      bool iter = ad_res.has("iteration");
      bool regression = ad_res.has("regression");
      if (ad_out.has("measure"))
	{
	  std::vector<std::string> measures = ad_out.get("measure").get<std::vector<std::string>>();
      	  bool bauc = (std::find(measures.begin(),measures.end(),"auc")!=measures.end());
	  bool bacc = (std::find(measures.begin(),measures.end(),"acc")!=measures.end());
	  bool bf1 = (std::find(measures.begin(),measures.end(),"f1")!=measures.end());
	  bool bmcll = (std::find(measures.begin(),measures.end(),"mcll")!=measures.end());
	  bool bgini = (std::find(measures.begin(),measures.end(),"gini")!=measures.end());
	  if (bauc) // XXX: applies two binary classification problems only
	    {
	      double mauc = auc(ad_res);
	      meas_out.add("auc",mauc);
	    }
	  if (bacc)
	    {
	      double macc = acc(ad_res);
	      meas_out.add("acc",macc);
	    }
	  if (bf1)
	    {
	      double f1,precision,recall,acc;
	      dMat conf_diag;
	      f1 = mf1(ad_res,precision,recall,acc,conf_diag);
	      meas_out.add("f1",f1);
	      meas_out.add("precision",precision);
	      meas_out.add("recall",recall);
	      meas_out.add("accp",acc);
	      //TODO: confusion matrix ?
	      if (std::find(measures.begin(),measures.end(),"cmdiag")!=measures.end())
		{
		  std::vector<double> cmdiagv;
		  for (int i=0;i<conf_diag.rows();i++)
		    cmdiagv.push_back(conf_diag(i,0));
		  meas_out.add("cmdiag",cmdiagv);
		}
	    }
	  if (bmcll)
	    {
	      double mmcll = mcll(ad_res);
	      meas_out.add("mcll",mmcll);
	    }
	  if (bgini)
	    {
	      double mgini = gini(ad_res,regression);
	      meas_out.add("gini",mgini);
	    }
	}
	if (loss)
	  meas_out.add("loss",ad_res.get("loss").get<double>()); // 'universal', comes from algorithm
	if (tloss)
	  meas_out.add("train_loss",ad_res.get("train_loss").get<double>());
	if (iter)
	  meas_out.add("iteration",ad_res.get("iteration").get<double>());
	std::vector<APIData> vad = { meas_out };
	out.add("measure",vad);
    }

    // measure: ACC
    static double acc(const APIData &ad)
    {
      double acc = 0.0;
      int batch_size = ad.get("batch_size").get<int>();
      for (int i=0;i<batch_size;i++)
	{
	  APIData bad = ad.getobj(std::to_string(i));
	  std::vector<double> predictions = bad.get("pred").get<std::vector<double>>();
	  int maxpr = std::distance(predictions.begin(),std::max_element(predictions.begin(),predictions.end()));
	  if (maxpr == bad.get("target").get<double>())
	    acc++;
	}
      return acc / static_cast<double>(batch_size);
    }

    // measure: F1
    static double mf1(const APIData &ad, double &precision, double &recall, double &acc, dMat &conf_diag)
    {
      int nclasses = ad.get("nclasses").get<int>();
      double f1=0.0;
      dMat conf_matrix = dMat::Zero(nclasses,nclasses);
      int batch_size = ad.get("batch_size").get<int>();
      for (int i=0;i<batch_size;i++)
	{
	  APIData bad = ad.getobj(std::to_string(i));
	  std::vector<double> predictions = bad.get("pred").get<std::vector<double>>();
	  int maxpr = std::distance(predictions.begin(),std::max_element(predictions.begin(),predictions.end()));
	  double target = bad.get("target").get<double>();
	  if (target < 0)
	    throw OutputConnectorBadParamException("negative supervised discrete target (e.g. wrong use of label_offset ?");
	  conf_matrix(maxpr,target) += 1.0;
	}
      conf_diag = conf_matrix.diagonal();
      dMat conf_csum = conf_matrix.colwise().sum();
      dMat conf_rsum = conf_matrix.rowwise().sum();
      dMat eps = dMat::Constant(nclasses,1,1e-8);
      acc = conf_diag.sum() / conf_matrix.sum();
      precision = conf_diag.transpose().cwiseQuotient(conf_csum + eps.transpose()).sum() / static_cast<double>(nclasses);
      recall = conf_diag.cwiseQuotient(conf_rsum + eps).sum() / static_cast<double>(nclasses);
      f1 = (2.0*precision*recall) / (precision+recall);
      conf_diag = conf_diag.transpose().cwiseQuotient(conf_csum+eps.transpose()).transpose();
      return f1;
    }
    
    // measure: AUC
    static double auc(const APIData &ad)
    {
      std::vector<double> pred1;
      std::vector<double> targets;
      int batch_size = ad.get("batch_size").get<int>();
      for (int i=0;i<batch_size;i++)
	{
	  APIData bad = ad.getobj(std::to_string(i));
	  pred1.push_back(bad.get("pred").get<std::vector<double>>().at(1));
	  targets.push_back(bad.get("target").get<double>());
	}
      return auc(pred1,targets);
    }
    static double auc(const std::vector<double> &pred, const std::vector<double> &targets)
    {
      class PredictionAndAnswer {
      public:
	PredictionAndAnswer(const float &f, const int &i)
	  :prediction(f),answer(i) {}
	~PredictionAndAnswer() {}
	float prediction;
	int answer; //this is either 0 or 1
      };
      std::vector<PredictionAndAnswer> p;
      for (size_t i=0;i<pred.size();i++)
	p.emplace_back(pred.at(i),targets.at(i));
      int count = p.size();
      
      std::sort(p.begin(),p.end(),
		[](const PredictionAndAnswer &p1, const PredictionAndAnswer &p2){return p1.prediction < p2.prediction;});

      int i,truePos,tp0,accum,tn,ones=0;
      float threshold; //predictions <= threshold are classified as zeros

      for (i=0;i<count;i++) ones+=p[i].answer;
      if (0==ones || count==ones) return 1;

      truePos=tp0=ones; accum=tn=0; threshold=p[0].prediction;
      for (i=0;i<count;i++) {
        if (p[i].prediction!=threshold) { //threshold changes
	  threshold=p[i].prediction;
	  accum+=tn*(truePos+tp0); //2* the area of trapezoid
	  tp0=truePos;
	  tn=0;
        }
        tn+= 1- p[i].answer; //x-distance between adjacent points
        truePos-= p[i].answer;            
      }
      accum+=tn*(truePos+tp0); //2* the area of trapezoid
      return accum/static_cast<double>((2*ones*(count-ones)));
    }
    
    // measure: multiclass logarithmic loss
    static double mcll(const APIData &ad)
    {
      double ll=0.0;
      int batch_size = ad.get("batch_size").get<int>();
      for (int i=0;i<batch_size;i++)
	{
	  APIData bad = ad.getobj(std::to_string(i));
	  std::vector<double> predictions = bad.get("pred").get<std::vector<double>>();
	  double target = bad.get("target").get<double>();
	  ll -= std::log(predictions.at(target));
	}
      return ll / static_cast<double>(batch_size);
    }

    static std::vector<double> linspace(double start, double end, double num)
    {
      double delta = (end - start) / (num - 1);
      std::vector<double> linspaced(num - 1);
      for(int i=0; i < num-1; ++i)
	{
	  linspaced[i] = start + delta * i;
	}
      linspaced.push_back(end);
      return linspaced;
    };
    
    // measure: gini coefficient
    static double comp_gini(const std::vector<double> &a, const std::vector<double> &p) {
      struct K {double a, p;} k[a.size()];
      for (size_t i = 0; i != a.size(); ++i) k[i] = {a[i], p[i]};
      std::stable_sort(k, k+a.size(), [](const K &a, const K &b) {return a.p > b.p;});
      double accPopPercSum=0, accLossPercSum=0, giniSum=0, sum=0;
      for (auto &i: a) sum += i;
      for (auto &i: k) 
	{
	  accLossPercSum += i.a/sum;
	  accPopPercSum += 1.0/a.size();
	  giniSum += accLossPercSum-accPopPercSum;
	}
      return giniSum/a.size();
    }
    static double comp_gini_normalized(const std::vector<double> &a, const std::vector<double> &p) {
      return comp_gini(a, p)/comp_gini(a, a);
    }
    
    static double gini(const APIData &ad,
		       const bool &regression)
    {
      int batch_size = ad.get("batch_size").get<int>();
      std::vector<double> a(batch_size);
      std::vector<double> p(batch_size);
      for (int i=0;i<batch_size;i++)
	{
	  APIData bad = ad.getobj(std::to_string(i));
	  a.at(i) = bad.get("target").get<double>();
	  if (regression)
	    p.at(i) = bad.get("pred").get<std::vector<double>>().at(0); //XXX: could be vector for multi-dimensional regression -> TODO: in supervised mode, get best pred index ?
	  else
	    {
	      std::vector<double> allpreds = bad.get("pred").get<std::vector<double>>();
	      a.at(i) = std::distance(allpreds.begin(),std::max_element(allpreds.begin(),allpreds.end()));
	    }
	}
      return comp_gini_normalized(a,p);
    }
    
    // for debugging purposes.
    /**
     * \brief print supervised output to string
     */
    void to_str(std::string &out) const
    {
      auto vit = _vcats.begin();
      while(vit!=_vcats.end())
	{
	  out += "-------------\n";
	  out += (*vit).first + "\n";
	  auto mit = _vvcats.at((*vit).second)._cats.begin();
	  while(mit!=_vvcats.at((*vit).second)._cats.end())
	  {
	    out += "accuracy=" + std::to_string((*mit).first) + " -- cat=" + (*mit).second + "\n";
	    ++mit;
	  }
	  ++vit;
	}
    }

    /**
     * \brief write supervised output object to data object
     * @param out data destination
     */
    void to_ad(APIData &out) const
    {
      static std::string cl = "classes";
      static std::string phead = "prob";
      static std::string chead = "cat";
      std::vector<APIData> vpred;
      
      //debug
      /*std::string str;
      to_str(str);
      std::cout << "witness=\n" << str << std::endl;*/
      //debug

      for (size_t i=0;i<_vvcats.size();i++)
	{
	  std::vector<APIData> v;
	  auto mit = _vvcats.at(i)._cats.begin();
	  while(mit!=_vvcats.at(i)._cats.end())
	    {
	      APIData nad;
	      nad.add(chead,(*mit).second);
	      nad.add(phead,(*mit).first);
	      v.push_back(nad);
	      ++mit;
	    }
	  APIData adpred;
	  adpred.add(cl,v);
	  if (_vvcats.at(i)._loss > 0.0) // XXX: not set by Caffe in prediction mode for now
	    adpred.add("loss",_vvcats.at(i)._loss);
	  adpred.add("uri",_vvcats.at(i)._label);
	  vpred.push_back(adpred);
	}
      out.add("predictions",vpred);
    }
    
    std::unordered_map<std::string,int> _vcats; /**< batch of results, per uri. */
    std::vector<sup_result> _vvcats; /**< ordered results, per uri. */
    
    // options
    int _best = 1;
  };
  
}

#endif
