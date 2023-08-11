package com.winterwell.datalog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.winterwell.datalog.server.DataServletTest;
import com.winterwell.es.client.ESConfig;
import com.winterwell.es.client.ESHttpClient;
import com.winterwell.es.client.SearchRequest;
import com.winterwell.es.client.SearchResponse;
import com.winterwell.es.client.agg.Aggregation;
import com.winterwell.gson.Gson;
import com.winterwell.nlp.query.SearchQuery;
import com.winterwell.utils.Printer;
import com.winterwell.utils.containers.ArrayMap;
import com.winterwell.utils.containers.Containers;
import com.winterwell.utils.web.SimpleJson;
import com.winterwell.utils.web.WebUtils2;

/**
 * See also {@link DataServletTest}
 * @author daniel
 *
 */
public class ESDataLogSearchBuilderTest {

	@Test
	public void testSumAll() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		
		List<String> breakdown = Arrays.asList("{\"dntn\":\"sum\"}");
		esdsb.setBreakdown(breakdown);
		
		List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
		String s = Printer.toString(aggs, "\n");
		assert aggs.size() == 2;
		Aggregation a0 = aggs.get(0);
		Printer.out(a0.toJson2());
		assert ! s.contains("by_");
	}

	

	@Test
	public void testRandom() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		
		List<String> breakdown = Arrays.asList("{\"dntn\":\"sum\"}");
		esdsb.setBreakdown(breakdown);
		
		esdsb.setRandomSampling(0.01, false);
		
		List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
		String s = Printer.toString(aggs, "\n");
		assert aggs.size() == 2;
		Aggregation a0 = aggs.get(0);
		Printer.out(a0.toJson2());
		assert ! s.contains("by_");
		
		SearchRequest sr = esdsb.prepareSearch();
		SearchResponse sresp = sr.get();
		Map respaggs = sresp.getAggregations();
		Printer.out(respaggs);
	}
	

	@Test
	public void testBuckets() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		List<String> breakdown = Arrays.asList("pub/time");
		esdsb.setBreakdown(breakdown);
		esdsb.setQuery(new SearchQuery(""));
		
		SearchRequest search = esdsb.prepareSearch();
		search.setIndex("datalog.gl");
		search.setDebug(true);		
		// Search!
		SearchResponse sr = search.get();		
		Map aggregations = sr.getAggregations();
		// strip out no0 filter wrappers
		aggregations = esdsb.cleanJson(aggregations);
		System.out.println(aggregations);
	}

	

	@Test
	public void testRuntimeMapping() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		List<String> breakdown = Arrays.asList("pub/timeofday");
		esdsb.setBreakdown(breakdown);
		esdsb.setQuery(new SearchQuery(""));
		
		SearchRequest search = esdsb.prepareSearch();
		search.setIndex("datalog.gl");
		search.setDebug(true);		
		// Search!
		SearchResponse sr = search.get();		
		Map aggregations = sr.getAggregations();
		// strip out no0 filter wrappers
		aggregations = esdsb.cleanJson(aggregations);
		System.out.println(aggregations);
	}

	
	@Test
	public void testClean() {
		{	// no breakdown
			String json = "{'all':{'min':1.0,'avg':1.0,'max':1.0,'count':25.0,'sum':25.0},'no0_0':{'count':25.0,'count':{'min':1.0,'avg':1.0,'max':1.0,'count':25.0,'sum':25.0}},'examples':[{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-15T13:54:21Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_771','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T10:23:10Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_507','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T10:27:20Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_99','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T10:42:36Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_836','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T10:43:31Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_827','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T11:16:33Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_370','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T11:33:51Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_615','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-15T13:49:32Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_316','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-15T16:12:35Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_253','_score':1.0},{'_index':'datalog.testspace_aug19','_type':'evt','_source':{'evt':['unittest'],'count':1.0,'host':'localhost','time':'2019-08-16T11:21:01Z','user':'DataServletTest@bot','props':[{'v':'init','k':'stage'}]},'_id':'testspace_unittest_fb628568b1cd43e736cbd9041a74f3a6_374','_score':1.0}]}".replace('\'', '"');
			ESHttpClient esc = new ESHttpClient(new ESConfig());
			ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
			Map aggregations = (Map) WebUtils2.parseJSON(json);
			Map clean = esdsb.cleanJson(aggregations);
			String cj = WebUtils2.generateJSON(clean);
			assert ! cj.contains("no0") : cj;
		}
		{
			String json = "{'buckets':[{'no0_0':{'count':20.0,'count':{'min':1.0,'avg':1.0,'max':1.0,'count':20.0,'sum':20.0}},'count':20.0,'key':'unittest'}]}".replace('\'', '"');
			ESHttpClient esc = new ESHttpClient(new ESConfig());
			ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
			Map aggregations = (Map) WebUtils2.parseJSON(json);
			Map clean = esdsb.cleanJson(aggregations);
			String cj = WebUtils2.generateJSON(clean);
			assert ! cj.contains("no0") : cj;
		}
		{
			String json = "{'evt':{'min':1.0,'avg':1.0,'max':1.0,'count':20.0,'sum':20.0},'by_evt':{'doc_count_error_upper_bound':0.0,'sum_other_doc_count':0.0,'buckets':[{'no0_0':{'doc_count':20.0,'count':{'min':1.0,'avg':1.0,'max':1.0,'count':20.0,'sum':20.0}},'count':20.0,'key':'unittest'}]}}".replace('\'', '"');
			ESHttpClient esc = new ESHttpClient(new ESConfig());
			ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
			Map aggregations = (Map) WebUtils2.parseJSON(json);
			Map clean = esdsb.cleanJson(aggregations);
			String cj = WebUtils2.generateJSON(clean);
			assert ! cj.contains("no0") : cj;
		}
	
	}
	
	@Test
	public void testSetBreakdownSimple() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		
		List<String> breakdown = Arrays.asList("evt");
		esdsb.setBreakdown(breakdown);
		
		List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
		String s = Printer.toString(aggs, "\n");
		Printer.out("evt:	"+s);
		assert aggs.size() == 2;
		Aggregation a0 = aggs.get(0);
		Printer.out(a0.toJson2());
		assert Containers.same((Map)a0.toJson2().get("terms"), 
				new ArrayMap("field", "evt", "missing", "unset")
				) : a0.toJson2();
		Aggregation a1 = aggs.get(1);
		Printer.out(a1.toJson2());
		assert Containers.same(a1.toJson2(), 
				new ArrayMap("sum", new ArrayMap("field", "count"))
				) : a1.toJson2();
	}

	@Test
	public void testSetBreakdownOverlap() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		
		List<String> breakdown = Arrays.asList("evt/host", "evt/user");
		esdsb.setBreakdown(breakdown);

		// TODO it would be nice if the parser was smart enough to merge these into a tree. Oh well.
		
		List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
		String s = Printer.toString(aggs, "\n");
		Printer.out("evt:	"+s);
		List<Object> names = Containers.apply(aggs,  agg -> agg.name);
		assert names.size() == new HashSet(names).size() : names;
		assert aggs.size() == 3;
	}


	@Test
	public void testSetBreakdownAByB() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));
		
		{
			List<String> breakdown = Arrays.asList("evt/time");
			esdsb.setBreakdown(breakdown);
			List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
			Map s = aggs.get(0).toJson2();
			Printer.out("evt/time:	"+s);
		}
		{	
			List<String> breakdown = Arrays.asList("frog/carrot/iron");
			esdsb.setBreakdown(breakdown);
			List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
			Map s = aggs.get(0).toJson2();
			Printer.out("\n"+breakdown+":	"+s);
		}
		{	
			List<String> breakdown = Arrays.asList("animal/vegetable {\"mycount\": \"avg\"}");
			esdsb.setBreakdown(breakdown);
			List<Aggregation> aggs = esdsb.prepareSearch2_aggregations();
			Map s = aggs.get(0).toJson2();
			Printer.out("\n"+breakdown+":	"+s);
		}
	}
	
	@Test
	public void testRoundingSigFig() {
		ESHttpClient esc = new ESHttpClient(new ESConfig());
		ESDataLogSearchBuilder esdsb = new ESDataLogSearchBuilder(esc, new Dataspace("test"));

		{
			String json = "{sampling={by_adid={doc_count_error_upper_bound=0.0, sum_other_doc_count=0.0, buckets=[{key=ywVwdFWP, co2supplypath=739.47218338796, co2base=570.5750453393206, co2=3673.3025099765173, count=5788572.7075, co2creative=2363.2552812492368}, {key=tD0g9TB8, co2supplypath=74.55523407067999, co2base=56.651628924662056, co2=365.3571858164172, count=583641.3308, co2creative=234.15032282107518}, {key=2EeAzeZT, co2supplypath=33.01112568844749, co2base=25.28567246826035, co2=164.64371064024238, count=258432.5447, co2creative=106.34691248353452}, {key=BET7o4GM, co2supplypath=615.3195294213824, co2base=475.8114164153575, co2=3043.5602682533718, count=4816697.0896, co2creative=1952.4293224166317}, {key=WC9zndZp, co2supplypath=37.131694340124994, co2base=28.481170820128746, co2=408.04651240729453, count=290659.05549999996, co2creative=342.4336472470408}, {key=2jZ2Pzof, co2supplypath=18.289616685724997, co2base=13.853863733958866, co2=125.06021281946629, count=143167.25389999998, co2creative=92.91673239978242}, {key=YCTHdrzT, co2supplypath=11.854966115299996, co2base=9.128580169542213, co2=119.49583433351123, count=92798.16919999999, co2creative=98.512288048669}, {key=JJngMpln, co2supplypath=0.04648538895, co2base=0.026037431731069777, co2=0.4343210878696556, count=363.8778, co2creative=0.3617982671885858}, {key=Hafimh5A, co2supplypath=0.019666895325, co2base=0.012096646637446707, co2=0.15492068852042282, count=153.9483, co2creative=0.1231571465579761}, {key=9mP8TqCO, co2supplypath=0.0017878995749999998, co2base=0.0010496891446811936, co2=0.009506816539107128, count=13.995299999999999, co2creative=0.006669227819425936}, {key=grBGY2BG, co2supplypath=5.959665249999999E-4, co2base=2.9476795652595597E-4, co2=0.002451522139165217, count=4.6651, co2creative=0.001560787657639261}]}, seed=-4.8768811E7, probability=0.21435767722020965, allCount=1.1974504637699999E7}}"
					.replace('\'', '"');
			Map<String, Object> agg = Gson.fromJSON(json);
			agg = esdsb.roundingSigFig(agg, 2);
			System.out.println(agg);

			Map<String, ?> sampling = SimpleJson.get(agg, "sampling");
			assert Double.valueOf(12000000).equals(sampling.get("allCount"));

			Map<String, Double> bucket0 = SimpleJson.get(agg, "sampling", "by_adid", "buckets", 0);
			assert Double.valueOf(740.0).equals(bucket0.get("co2supplypath"));
			assert Double.valueOf(570.0).equals(bucket0.get("co2base"));
			assert Double.valueOf(3700.0).equals(bucket0.get("co2"));
			assert Double.valueOf(5800000.0).equals(bucket0.get("count"));
			assert Double.valueOf(2400.0).equals(bucket0.get("co2creative"));
			assert Double.valueOf(740.0).equals(bucket0.get("co2supplypath"));

		}
	}
}
