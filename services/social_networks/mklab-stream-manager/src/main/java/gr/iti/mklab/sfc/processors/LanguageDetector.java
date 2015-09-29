package gr.iti.mklab.sfc.processors;

import org.apache.log4j.Logger;

import gr.iti.mklab.framework.common.domain.Item;
import gr.iti.mklab.framework.common.domain.config.Configuration;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;


public class LanguageDetector extends Processor {

	public LanguageDetector(Configuration configuration) {
		super(configuration);
		String profileDirectory = configuration.getParameter("profileDirectory",
				"profiles.sm");
		try {
			DetectorFactory.loadProfile(profileDirectory);
		} catch (LangDetectException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void process(Item item) {
		String lang = item.getLanguage();
		if(lang == null) {
			// detect lang
			String text = null;
			String title = item.getTitle();
			String description = item.getDescription();
			
			if(title != null) {
				text = title;
			}
			else if (description != null) {
				text = description;
			}
			else {
				return;
			}
			
			try {
				Detector detector = DetectorFactory.create();
				
				detector.append(text);
				lang = detector.detect();
				item.setLanguage(lang);
				
			} catch (LangDetectException e) {
				Logger.getLogger(LanguageDetector.class).info("No features in text: " + text);
			}
		}
	}

}
