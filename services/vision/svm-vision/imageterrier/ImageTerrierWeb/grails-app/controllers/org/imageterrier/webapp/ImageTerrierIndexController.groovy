package org.imageterrier.webapp

import org.imageterrier.webapp.ImageTerrierIndex
import org.imageterrier.webapp.ResultsProcessor
import grails.converters.*
import org.springframework.web.multipart.MultipartHttpServletRequest 
import grails.plugins.springsecurity.Secured
import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Permission
import org.springframework.transaction.annotation.Transactional
class ImageTerrierIndexController {

	static allowedMethods = [save: "POST", update: "POST", delete: "POST"]
	def imageTerrierIndexService
	def aclUtilService
	def springSecurityService
	def sessionFactory
	def aclPermissionFactory
	def aclService
	
	def index = {
		redirect(action: "list", params: params)
	}

	def search = {
/*		JSON.setPrettyPrint(true)*/
		log.info("starting search")
		
		// apply the results processor
		log.info("ResultsProcessor loading: '"+params.resultsProcessor+"'")
		def resultsProcessor = ResultsProcessor.loadProcessor(params.resultsProcessor)
		log.info("ResultsProcessor found was: '"+resultsProcessor+"'")
		
		def terrierIndexInstance = null;
		try{
			log.info("trying to load by id: " + params.id)
			terrierIndexInstance = ImageTerrierIndex.get(Integer.parseInt(params.id))
		}
		catch(Exception e){
			log.info("Could not load by number id")
		}
		if(terrierIndexInstance == null){
			try{
				terrierIndexInstance = ImageTerrierIndex.findByShortName(params.id)
			}
			catch(Exception e){
				log.info("Could not load by name id")
			}
		}
		if(terrierIndexInstance == null){
			def allData = ["error":["message":"index does not exist by id or name: " + params.id]]
			withFormat {
				html { return [ data : allData ] }
				xml { render allData as XML }
				json { render allData as JSON }
			}
			return
		}
		log.info("Index found")

		def tmpFile = null;
		if(params.imageURL != null && params.imageURL != ""){
			def imageURL = params.imageURL.replace(" ","+")
			log.info("Loading query from url: " + imageURL)
			tmpFile = File.createTempFile("image", "file")
			def out = new BufferedOutputStream(new FileOutputStream(tmpFile));
			out << new URL(imageURL).openStream();
			out.close();
		}
		else if(request instanceof MultipartHttpServletRequest){
			tmpFile = File.createTempFile("image", "file")
			def f = request.getFile('imageFile');
			def os = tmpFile.newOutputStream()
			def bytes = f.getBytes()
			os.write(bytes, 0, bytes.length)
			os.close()
		}
		else{
			log.error("Query was not correctly provided");
			def allData = ["error":["message":"No ImageURL or ImageFile found!"]]
			withFormat {
				html { return [ data : allData ] }
				xml { render allData as XML }
				json { render allData as JSON }
			}
			return;
		}
		
		log.info("Image successfully loaded, loading properties")
		def queryOption = null;
		if(params.queryOption != null && params.queryOption != ""){
			queryOption = QueryOptions.get(params.queryOption)
		}
		if(queryOption == null){
			log.info("Loading default query option")
			queryOption = QueryOptions.getAll()[0];
		}
		if(queryOption == null){
			queryOption = ""
		}
		else{
			queryOption = queryOption.options
		}
		
		log.info("Query options loaded: '"+queryOption+"', finding limit")
		def limit = 10;
		if(params.limit != null && params.limit != ""){
			limit = Integer.parseInt(params.limit)
		}
		
		def results = terrierIndexInstance.search(tmpFile,queryOption,limit)
		def allData = [
			"results":results,
		]
		
		resultsProcessor.process(allData)
		withFormat {
			html { return [ data : allData ] }
			xml { render allData as XML }
			json { render allData as JSON }
		}
	}

	def list = {
		params.max = Math.min(params.max ? params.int('max') : 10, 100)
		[imageTerrierIndexInstanceList: ImageTerrierIndex.list(params), imageTerrierIndexInstanceTotal: ImageTerrierIndex.count()]
	}
	
	def create = {
		redirect(controller: "import", action: "index")
	}

	def show = {
		def imageTerrierIndexInstance = ImageTerrierIndex.get(params.id)
		if (!imageTerrierIndexInstance) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'imageTerrierIndex.label', default: 'ImageTerrierIndex'), params.id])}"
			redirect(action: "list")
		}
		else {
			[imageTerrierIndexInstance: imageTerrierIndexInstance]
		}
	}
	
	@Secured(['ROLE_ADMIN','ROLE_INDEXER'])
	def delete = {
		def imageTerrierIndexInstance = imageTerrierIndexService.get(params.long("id"))
		if (imageTerrierIndexInstance) {
			try {
				imageTerrierIndexInstance.delete(flush: true)
				flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'imageTerrierIndex.label', default: 'ImageTerrierIndex'), params.id])}"
				redirect(action: "list")
			}
			catch (org.springframework.dao.DataIntegrityViolationException e) {
				flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'imageTerrierIndex.label', default: 'ImageTerrierIndex'), params.id])}"
				redirect(action: "show", id: params.id)
			}
		}
		else {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'imageTerrierIndex.label', default: 'ImageTerrierIndex'), params.id])}"
			redirect(action: "list")
		}
	}
}
