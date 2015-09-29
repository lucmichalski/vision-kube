package eu.europeana.cloud.service.uis.persistent;

import eu.europeana.cloud.service.uis.persistent.CassandraDataProviderService;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.io.BaseEncoding;

import eu.europeana.cloud.common.exceptions.ProviderDoesNotExistException;
import eu.europeana.cloud.common.model.DataProvider;
import eu.europeana.cloud.common.model.DataProviderProperties;
import eu.europeana.cloud.common.response.ResultSlice;
import eu.europeana.cloud.service.uis.exception.ProviderAlreadyExistsException;

/**
 * DataProvider Service Test
 * 
 * @author Yorgos Mamakis (Yorgos.Mamakis@ europeana.eu)
 * @since Jan 10, 2014
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(value = { "classpath:/default-context.xml" })
public class CassandraDataProviderServiceTest extends CassandraTestBase {

	
	@Autowired
	private CassandraDataProviderService cassandraDataProviderService;

	/**
	 * Create and get a provider
	 * 
	 * @throws ProviderAlreadyExistsException
	 * @throws ProviderDoesNotExistException
	 */
	@Test
	public void shouldCreateAndGetProvider() throws ProviderAlreadyExistsException, ProviderDoesNotExistException {
		String id = "provident";
                DataProvider dp = cassandraDataProviderService
				.createProvider(id, createRandomDataProviderProperties());

		DataProvider dpFromService = cassandraDataProviderService.getProvider("provident");
		assertThat(dp, is(dpFromService));
                assertThat(dp.getPartitionKey(), is(id.hashCode()));
	}

	/**
	 * Test provider does not exist exception
	 * 
	 * @throws ProviderDoesNotExistException
	 */
	@Test(expected = ProviderDoesNotExistException.class)
	public void shouldFailWhenFetchingNonExistingProvider() throws ProviderDoesNotExistException {
		cassandraDataProviderService.getProvider("provident");
	}

	/**
	 * Empty array retrieval
	 */
	@Test
	public void shouldReturnEmptyArrayWhenNoProviderAdded() {
		assertTrue("Expecting no providers", cassandraDataProviderService.getProviders(null, 1).getResults().isEmpty());
	}

	/**
	 * Retrieve batch providers test
	 * @throws ProviderAlreadyExistsException
	 */
	@Test
	public void shouldReturnAllProviders() throws ProviderAlreadyExistsException {
		int providerCount = 10;
		Set<DataProvider> insertedProviders = new HashSet<>(providerCount * 2);

		// insert random providers
		for (int provId = 0; provId < providerCount; provId++) {
			insertedProviders.add(cassandraDataProviderService.createProvider("dp_" + provId,
					createRandomDataProviderProperties()));
		}

		Set<DataProvider> fetchedProviders = new HashSet<>(cassandraDataProviderService.getProviders(null, 100)
				.getResults());
		assertThat(fetchedProviders, is(insertedProviders));
	}

	/**
	 * Paged Provider list retrieval
	 * 
	 * @throws ProviderAlreadyExistsException
	 */
	@Test
	public void shouldReturnPagedProviderList() throws ProviderAlreadyExistsException {
		int providerCount = 100;
		List<String> insertedProviderIds = new ArrayList<>(providerCount);

		// insert random providers
		for (int provId = 0; provId < providerCount; provId++) {
			DataProvider prov = cassandraDataProviderService.createProvider("dp_" + provId,
					createRandomDataProviderProperties());
			insertedProviderIds.add(prov.getId());
		}

		// iterate through all providers
		List<String> fetchedProviderIds = new ArrayList<>(providerCount);
		int sliceSize = 10;
		String token = null;
		do {
			ResultSlice<DataProvider> resultSlice = cassandraDataProviderService.getProviders(token, sliceSize);
			token = resultSlice.getNextSlice();
			assertTrue(resultSlice.getResults().size() == sliceSize || token == null);
			for (DataProvider dp : resultSlice.getResults()) {
				fetchedProviderIds.add(dp.getId());
			}
		} while (token != null);

		Collections.sort(insertedProviderIds);
		Collections.sort(fetchedProviderIds);
		assertThat(insertedProviderIds, is(fetchedProviderIds));
	}

	@Test(expected = ProviderDoesNotExistException.class)
	public void shouldDeleteProvider() throws ProviderAlreadyExistsException, ProviderDoesNotExistException{
		String id = "sampleProvider";
		DataProvider dp = cassandraDataProviderService
				.createProvider(id, createRandomDataProviderProperties());

		DataProvider dpFromService = cassandraDataProviderService.getProvider("sampleProvider");
		assertThat(dp, is(dpFromService));
		assertThat(dp.getPartitionKey(), is(id.hashCode()));
		//
		cassandraDataProviderService.deleteProvider("sampleProvider");
		cassandraDataProviderService.deleteProvider("sampleProvider");
	}
	
	@Test(expected = ProviderDoesNotExistException.class)
	public void shouldThrowExceptionWhenDeletingNonExistingProvider() throws ProviderDoesNotExistException {
		cassandraDataProviderService.deleteProvider("not existing provident");
	}

	private DataProviderProperties createRandomDataProviderProperties() {
		DataProviderProperties properties = new DataProviderProperties();
		properties.setContactPerson("Contact_Person_" + randomString());
		properties.setDigitalLibraryURL("http://library.url/" + randomString());
		properties.setDigitalLibraryWebsite("http://library.url/website/" + randomString());
		properties.setOfficialAddress("Address/" + randomString());
		properties.setOrganisationName("Organisation_Name_" + randomString());
		properties.setOrganisationWebsite("http://organisation.url/" + randomString());
		properties.setOrganisationWebsiteURL("http://organisation.url/website" + randomString());
		properties.setRemarks("Important remarks for provider include " + randomString());
		return properties;
	}

	private static String randomString() {
		byte[] randomBytes = new byte[10];
		ThreadLocalRandom.current().nextBytes(randomBytes);
		return BaseEncoding.base64().encode(randomBytes);
	}
}
