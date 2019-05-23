package ch.specchio.factories;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ch.specchio.eav_db.AVSorter;
import ch.specchio.eav_db.EAVDBServices;
import ch.specchio.eav_db.SQL_StatementBuilder;
import ch.specchio.queries.EAVQueryConditionObject;
import ch.specchio.queries.Query;
import ch.specchio.queries.QueryCondition;
import ch.specchio.queries.QueryConditionObject;
import ch.specchio.queries.SpectrumQueryCondition;
import ch.specchio.types.AVMatchingList;
import ch.specchio.types.AVMatchingListCollection;
import ch.specchio.types.EAVTableAndRelationsInfoStructure;
import ch.specchio.types.Instrument;
import ch.specchio.types.MetaDatatype;
import ch.specchio.types.MetaDate;
import ch.specchio.types.MetaParameter;
import ch.specchio.types.MetaParameterFormatException;
import ch.specchio.types.MetadataSelectionDescriptor;
import ch.specchio.types.Picture;
import ch.specchio.types.PictureTable;
import ch.specchio.types.Sensor;
import ch.specchio.types.Spectrum;
import ch.specchio.types.SpectrumDataLink;
import ch.specchio.types.SpectrumFactorTable;

import org.joda.time.DateTime;


/**
 * Class for manipulating spectra stored in the database.
 */
public class SpectrumFactory extends SPECCHIOFactory {
	
	/** metadata factory for loading spectrum metadata */
	private MetadataFactory MF;
	private Integer current_spectrum_collection_table_no;
	private String current_spectrum_collection_source_table;
	private String current_spectrum_collection_target_table;
	private boolean iteration_result_exists, standardQueryStringExists;
	private Integer current_hierarchy_collection_table_no;
	private String current_hierarchy_collection_source_table;
	private String current_hierarchy_collection_target_table;
	
	
	/**
	 * Constructor. 
	 * 
	 * @param db_user		database account user name
	 * @param db_password	database account password
	 * @param is_admin	is the user an administrator? 
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public SpectrumFactory(String db_user, String db_password, String ds_name, boolean is_admin) throws SPECCHIOFactoryException {

		super(db_user, db_password, ds_name, is_admin);
		
		MF = new MetadataFactory(this);
		
	}
	
	
	/**
	 * Copy constructor. Construct a new factory that uses the same database connection
	 * as an existing factory.
	 * 
	 * @param factory	the existing factory
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public SpectrumFactory(SPECCHIOFactory factory) throws SPECCHIOFactoryException {
		
		super(factory);
		
		MF = new MetadataFactory(this);
		
	}
	
	
	
	/**
	 * Build a query string from a Query object, using only non EAV conditions
	 * 
	 * @param query		the query
	 * @param attr		the attributes to use in building this query
	 * 
	 * @return an SQL query corresponding to the input object
	 * 
	 * @throws SQLException	database error
	 */
	private String buildNonEAVQuery(Query query) throws SQLException {
		
		SQL_StatementBuilder SB = getStatementBuilder();
		
		ArrayList<String> tables = new ArrayList<String>();
		String conds = "";
		String qco_cond = "";
		boolean valid_conds_available = false;
		
		
		// make sure we have the primary key name for the table
		String primary_key_name = SB.get_primary_key_name(query.getTableName()); 
		
		// collect information from all standard condition fields
		for (QueryCondition cond : query.getStandardConditionFields()) {
			EAVQueryConditionObject co = (EAVQueryConditionObject)cond;
			
			tables = co.get_tablenames();
			
			if(!co.getValue().equals("0")) // this assumes that only foreign key conditions are added here, and hence, zero keys stemming from NIL selections in comboboxes are not added as conditions
			{
				qco_cond = SB.prefix(co.getTableName(), co.getFieldName()) + " " + co.getOperator() + " " + (co.QuoteValue() ? SB.quote_value(co.getValue()) : co.getValue());
				conds = SB.conc_cond(conds, qco_cond);		
				valid_conds_available = true;
			}
		}	
		
		// spectrum or frame must always be in the table list
		if(!tables.contains(query.getTableName()))
			tables.add(query.getTableName()); 
		
		
		String queryString = "";
		
		if(valid_conds_available)
		{
		
			if (conds.equals(""))
			{
				// create default condition, as otherwise all spectra would be selected due to missing restrictions
				conds = SB.prefix(query.getTableName(), primary_key_name) + " = 0";
			}
			
			// get table foreign key joins
			String table_conds = SB.get_key_joins(tables.toArray(new String[tables.size()]));
			conds = SB.conc_cond(conds, table_conds);
				
			
			if (Query.COUNT_QUERY.equals(query.getQueryType())) {
				
				// assemble count query
				queryString = SB.assemble_sql_select_query("distinct count(*)", SB.conc_tables(tables), conds);
				
			} else if (Query.SELECT_QUERY.equals(query.getQueryType())) {
				
				// work out column names
				ArrayList<String> cols = new ArrayList<String>(query.getColumns().size());
				for (String col : query.getColumns()) {
					cols.add(SB.prefix(query.getTableAlias(), col));
				}
				
				// assemble plain query
				queryString = SB.assemble_sql_select_query(
						SB.conc_cols(cols),
						SB.conc_tables(tables),
						conds,
						SB.build_order_by_string(null)
					);
					
			} else {
				
				throw new SQLException("Invalid query type.");
				
			}
		}
		
		return queryString;
		
	}	
	
	
	/**
	 * Get the number of spectra that match a given query.
	 * 
	 * @param query		the query
	 * 
	 * @return the number of spectra in the database that match the given query
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public int countIdsMatchingQuery(Query query) throws SPECCHIOFactoryException {
		
		// sanity check
		if (!query.getQueryType().equals(Query.COUNT_QUERY)) {
			throw new SPECCHIOFactoryException("Not a count query.");
		}
		
		return getSpectraMatchingQuery(query).get(0);
		
	}
	
	
	/**
	 * Creates a copy of a spectrum in the specified hierarchy
	 * 
	 * @param spectrum_id		the spectrum_id of the spectrum to copy
	 * @param target_hierarchy_id	the hierarchy_id where the copy is to be stored
	 * 
	 * @return new spectrum id
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */	
	public int copySpectrum(int spectrum_id, int target_hierarchy_id) throws SPECCHIOFactoryException {
		
		int copy_spectrum_id = 0;
		
				
		
		String query = "INSERT INTO spectrum_view ("
		+ " hierarchy_level_id, sensor_id, campaign_id, "
		+ "file_format_id, instrument_id, calibration_id, "
		+ "measurement_unit_id, measurement) "
		+ "select "
		+ " hierarchy_level_id, sensor_id, campaign_id, "
		+ "file_format_id, instrument_id, calibration_id, "
		+ "measurement_unit_id, measurement "
		+ " from " + (this.Is_admin()?"spectrum":"spectrum_view") + " where spectrum_id = " + spectrum_id;
		
		Statement stmt;
		try {
			stmt = getStatementBuilder().createStatement();
			
			
			int no_of_inserted_rows = stmt.executeUpdate(query);
			
			if(no_of_inserted_rows > 0)
			{
				ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()");
				while (rs.next())
					copy_spectrum_id = rs.getInt(1);
				rs.close();

				stmt.close();		


				// copy all eav references at spectrum level without inherited eav data 
				ArrayList<Integer> eav_ids = getEavServices().get_eav_ids(MetaParameter.SPECTRUM_LEVEL, spectrum_id, false); // false = no inheritance
				getEavServices().insert_primary_x_eav(MetaParameter.SPECTRUM_LEVEL, copy_spectrum_id, eav_ids);

				// exchange hierarchy id
				query = "update " + (this.Is_admin()?"spectrum":"spectrum_view") + " set hierarchy_level_id = " + target_hierarchy_id + " where spectrum_id = " + copy_spectrum_id;

				stmt = getStatementBuilder().createStatement();
				stmt.executeUpdate(query);
				stmt.close();	

				// update the aggregated info in the upper hierarchies
				SpectralFileFactory sf_factory = new SpectralFileFactory(this);			
				sf_factory.insertHierarchySpectrumReferences(target_hierarchy_id, copy_spectrum_id, 0);
			}
			else
			{
				String msg = "Missing user rights: copying spectra is not allowed. Ask the owner of the dataset to be added to the research group.";
				SPECCHIOFactoryException e = new SPECCHIOFactoryException(msg);
				throw(e);
			}
			
			
		} catch (SQLException e) {
			throw new SPECCHIOFactoryException(e);
		}		
		
		
		return copy_spectrum_id;
	}
	
	
	/**
	 * Delete target-reference links.
	 * 
	 * @param eav_id		the eav_id identifier
	 * 
	 * @return the number of links deleted
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public int deleteTargetReferenceLinks(int eav_id) throws SPECCHIOFactoryException {
		
		int n = 0;
		
//		try {
//			Statement stmt = getStatementBuilder().createStatement();
//			String query = "delete from spectrum_datalink_view where spectrum_id=" + Integer.toString(target_id);
//			n = stmt.executeUpdate(query);
//			stmt.close();
//		}
//		catch (SQLException ex) {
//			// database error
//			throw new SPECCHIOFactoryException(ex);
//		}
		
		MetaParameter mp = MetaParameter.newInstance();
		mp.setEavId(eav_id);
		this.MF.removeMetadata(mp );
		
		n++;
		
		return n;
		
	}

	
	/**
	 * Get the spectrum identifiers that do have a reference to the specified attribute.
	 * 
	 * @param MetadataSelectionDescriptor 	specifies ids to filter and attribute to filter by
	 * 
	 * @return an array list of spectrum identifiers that match the filter
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ArrayList<Integer> filterSpectrumIdsByHavingAttribute(MetadataSelectionDescriptor mds) throws SPECCHIOFactoryException {
		
		EAVDBServices eav = getEavServices();					
		return eav.filter_by_eav(MetaParameter.SPECTRUM_LEVEL, mds.getIds(), mds.getAttribute_id());		
	}	
		
	
	/**
	 * Get the spectrum identifiers that do not have a reference to the specified attribute.
	 * 
	 * @param MetadataSelectionDescriptor 	specifies ids to filter and attribute to filter by
	 * 
	 * @return an array list of spectrum identifiers that match the filter
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ArrayList<Integer> filterSpectrumIdsByNotHavingAttribute(MetadataSelectionDescriptor mds) throws SPECCHIOFactoryException {
		
		EAVDBServices eav = getEavServices();					
		return eav.filter_by_attribute_NOT(MetaParameter.SPECTRUM_LEVEL, mds.getIds(), mds.getAttribute_id());		
	}	
	
	
	/**
	 * Get the spectrum identifiers that do reference to the specified attribute of a specified value.
	 * 
	 * @param MetadataSelectionDescriptor 	specifies ids to filter and attribute and value to filter by
	 * 
	 * @return an array list of spectrum identifiers that match the filter
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public ArrayList<Integer> filterSpectrumIdsByHavingAttributeValue(MetadataSelectionDescriptor mds) throws SPECCHIOFactoryException {
		
		EAVDBServices eav = getEavServices();					
		return eav.filter_by_eav(MetaParameter.SPECTRUM_LEVEL, mds.getIds(), mds.getAttribute_id(), eav.ATR.get_default_storage_field(mds.getAttribute_id()), mds.getValue());		
	}	
	
		
	
	/**																																																																																																												/**
	 * Get the identifiers of all spectra that match a full text search.
	 * 
	 * @param search_str		the search string
	 * 
	 * @return an array of identifiers
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */
	public ArrayList<Integer> getSpectrumIdsMatchingFullTextSearch(String search_str) throws SPECCHIOFactoryException {
		
		ArrayList<Integer> ids = new ArrayList<Integer>();	
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
		
			String query = "select distinct spectrum_x_eav.spectrum_id, eav.string_val from spectrum_x_eav, eav where spectrum_x_eav.eav_id = eav.eav_id and eav.string_val like '" + search_str + "'";
			
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				ids.add(rs.getInt(1));
			}
			rs.close();							
		
			stmt.close();			
			
		}
		catch (SQLException ex) {
			// database error
			System.out.println(ex.toString());
			throw new SPECCHIOFactoryException(ex);
		}			
		
		return ids;
		
	}	
	
	/**
	 * Helper method for countIdsMatchQuery() and getIdsMatchingQuery().
	 * 
	 * @param query	the query
	 * 
	 * @return a list of identifiers for a select query, or a "list" containing one record count for a count query
	 *
	 * @throws SPECCHIOFactoryException	database error
	 */
	private ArrayList<Integer> getSpectraMatchingQuery(Query query) throws SPECCHIOFactoryException {
	
		int count = 0;
		ArrayList<Integer> ids = new ArrayList<Integer>();	
		
		try {
			SQL_StatementBuilder SB = getStatementBuilder();
			Statement stmt = getStatementBuilder().createStatement();
			
			// iterative querying
			// ==================
			current_spectrum_collection_table_no = 0;
			current_spectrum_collection_target_table = SB.prefix(getTempDatabaseName(), "spectrum_collection" + current_spectrum_collection_table_no.toString());
			current_spectrum_collection_source_table = "";	
			
			current_hierarchy_collection_table_no = 0;
			current_hierarchy_collection_target_table = SB.prefix(getTempDatabaseName(), "hierarchy_collection" + current_hierarchy_collection_table_no.toString());
			current_hierarchy_collection_source_table = "";	
			
			EAVTableAndRelationsInfoStructure eav_info_0 = new EAVTableAndRelationsInfoStructure(this.getEavServices().getEAVTableAndRelationsInfoStructure(MetaParameter.SPECTRUM_LEVEL));
			EAVTableAndRelationsInfoStructure eav_info_1 = new EAVTableAndRelationsInfoStructure(this.getEavServices().getEAVTableAndRelationsInfoStructure(MetaParameter.HIERARCHY_LEVEL));
			
			// create temporary tables

			String ddl_string = "CREATE TEMPORARY TABLE IF NOT EXISTS " +
					SB.prefix(getTempDatabaseName(), "spectrum_collection0") +
					"(" + eav_info_0.primary_id_name + " INT NOT NULL, PRIMARY KEY (" + eav_info_0.primary_id_name + "))";
			stmt.executeUpdate(ddl_string);
			
			ddl_string = "CREATE TEMPORARY TABLE IF NOT EXISTS " +
					SB.prefix(getTempDatabaseName(), "spectrum_collection1") +
					"(" + eav_info_0.primary_id_name + " INT NOT NULL, PRIMARY KEY (" + eav_info_0.primary_id_name + "))";
			stmt.executeUpdate(ddl_string);
			
			ddl_string = "CREATE TEMPORARY TABLE IF NOT EXISTS " +
					SB.prefix(getTempDatabaseName(), "hierarchy_collection0") +
					"(" + eav_info_1.primary_id_name + " INT NOT NULL)";
			stmt.executeUpdate(ddl_string);
			
			ddl_string = "CREATE TEMPORARY TABLE IF NOT EXISTS " +
					SB.prefix(getTempDatabaseName(), "hierarchy_collection1") +
					"(" + eav_info_1.primary_id_name + " INT NOT NULL)";
			stmt.executeUpdate(ddl_string);			
			
			// clear temporary tables (in case they already existed)
			String delete_string = "delete from " + SB.prefix(getTempDatabaseName(), "spectrum_collection0");
			stmt.executeUpdate(delete_string);
			delete_string = "delete from " + SB.prefix(getTempDatabaseName(), "spectrum_collection1");
			stmt.executeUpdate(delete_string);		
			delete_string = "delete from " + SB.prefix(getTempDatabaseName(), "hierarchy_collection0");
			stmt.executeUpdate(delete_string);
			delete_string = "delete from " + SB.prefix(getTempDatabaseName(), "hierarchy_collection1");
			stmt.executeUpdate(delete_string);						
			
			//iteration_result_exists = true;
			standardQueryStringExists = false;
			

			// step 1.1: should restrict by spectrum_ids if such a condition exists (to speed up selection)
			
			
			// step 2: EAV iterations over all conditions for the hierarchy based EAVs
			getSpectraMatchingQuery(query, MetaParameter.HIERARCHY_LEVEL);
			
			
			
			// step 3: EAV iterations over all conditions
			getSpectraMatchingQuery(query, MetaParameter.SPECTRUM_LEVEL);
			
			
			// step 1.2: use standard field query, if there are any standard conditions at all
			if(query.getStandardConditionFields().size() > 0)
			{
				String org_query_type = query.getQueryType();
				query.setQueryType(Query.SELECT_QUERY); // we want to get ids!
				
				if(this.iteration_result_exists)
				{
					// switch source and target
//					current_spectrum_collection_table_no = (current_spectrum_collection_table_no + 1) % 2;
//					current_spectrum_collection_source_table = current_spectrum_collection_target_table;
//					current_spectrum_collection_target_table = SB.prefix(getTempDatabaseName(), "spectrum_collection" + current_spectrum_collection_table_no.toString());

					
					SpectrumQueryCondition condition = new SpectrumQueryCondition(this.current_spectrum_collection_source_table, "spectrum_id");
					condition.setOperator("=");
					condition.setValue(query.getTableName()+".spectrum_id");
					//condition.setTableName(query.getTableName());
					condition.setQuoteValue(false);
					query.add_condition(condition);
				}
				
				
//				QueryConditionObject cond = new QueryConditionObject(this.current_spectrum_collection_source_table, "spectrum)_id");
//				cond.setOperator("=");
//				
////				query.add_condition(cond);
				
				//query.add_join(this.current_spectrum_collection_source_table, condition);
				
				String standardQueryString = buildNonEAVQuery(query);
				
				EAVTableAndRelationsInfoStructure eav_info = new EAVTableAndRelationsInfoStructure(this.getEavServices().getEAVTableAndRelationsInfoStructure(MetaParameter.SPECTRUM_LEVEL));
				String primary_key_name = eav_info.primary_id_name;
				String primary_x_eav_table_name = eav_info.primary_x_eav_tablename;
				
				if(!standardQueryString.equals(""))
				{
					// clear current target table
					delete_string = "delete from " + current_spectrum_collection_target_table;
					stmt.executeUpdate(delete_string);						
					
					
					String insertQueryString = "insert into " + current_spectrum_collection_target_table + "("+ eav_info_0.primary_id_name + ") " + standardQueryString;
					stmt.executeUpdate(insertQueryString);
					
					// count ids for that standard query
					ResultSet rs = stmt.executeQuery("select count(*) from " + current_spectrum_collection_target_table);
					while (rs.next()) {
						count = rs.getInt(1);
						iteration_result_exists = true;
					}
					rs.close();	
					
					standardQueryStringExists = true;
					
					// switch source and target: the final data must be in the source for the final selection step further down
					current_spectrum_collection_table_no = (current_spectrum_collection_table_no + 1) % 2;
					current_spectrum_collection_source_table = current_spectrum_collection_target_table;
					current_spectrum_collection_target_table = SB.prefix(getTempDatabaseName(), "spectrum_collection" + current_spectrum_collection_table_no.toString());
					
				
				}
				else
				{
					//iteration_result_exists = false;
				}
				
				query.setQueryType(org_query_type);
				
			}
			else
			{
				//iteration_result_exists = false;
			}			
			

			if(query.getQueryType().equals(Query.COUNT_QUERY))
			{				
				ResultSet rs = stmt.executeQuery("select count(*) from " + current_spectrum_collection_source_table);
				while (rs.next()) {
					count = rs.getInt(1);
				}
				rs.close();	
			}
			else
			{
				// only get final result if there were any iteration results at all
				if(this.iteration_result_exists)
				{
					// build query of the final table
					StringBuffer queryBuffer = new StringBuffer();
					queryBuffer.append(
							"select " +
									SB.prefix(current_spectrum_collection_source_table, eav_info_0.primary_id_name) +
									" from " +
									current_spectrum_collection_source_table
							);

					if (query.getOrderBy() != null && (query.getOrderBy().length() > 0)) {
						// join to the attribute by which ordering will be done
						int order_by_attribute_id = getAttributes().get_attribute_id(query.getOrderBy());
						String order_by_storage_field = getAttributes().get_default_storage_field(order_by_attribute_id);
						queryBuffer.append(
								" left join (" +
										"select spectrum_x_eav.spectrum_id, eav.eav_id, eav." + order_by_storage_field + " " +
										"from spectrum_x_eav, eav " +
										"where spectrum_x_eav.eav_id = eav.eav_id " +
										"and eav.attribute_id = " + order_by_attribute_id +
										") t " +
										"on " +
										SB.prefix("t", eav_info_0.primary_id_name) +
										"=" +
										SB.prefix(current_spectrum_collection_source_table, eav_info_0.primary_id_name)
								);

						// add "order by" clause
						queryBuffer.append(" order by " + SB.prefix("t", order_by_storage_field));
					}


					ResultSet rs = stmt.executeQuery(queryBuffer.toString());
					while (rs.next()) {
						ids.add(rs.getInt(1));
					}
					rs.close();		
				}
			}			

 			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			System.out.println(ex.toString());
			throw new SPECCHIOFactoryException(ex);
		}		
		
		
		if(query.getQueryType().equals(Query.COUNT_QUERY))
		{
			ArrayList<Integer> counts = new ArrayList<Integer>(1);
			counts.add(count);
			return counts;
		}
		else
		{
			return ids;			
		}
	
	
	}
		


	private void getSpectraMatchingQuery(Query query, int metadata_level) {
		
		Integer current_collection_table_no;
		String current_collection_source_table;	
		String current_collection_target_table;
		String current_collection_table_name;
		boolean update_collection = true;
		int count_of_rows_in_target_table = 0;
		
		try {
			SQL_StatementBuilder SB = getStatementBuilder();
			Statement stmt = getStatementBuilder().createStatement();
			
			if(metadata_level == MetaParameter.SPECTRUM_LEVEL)
			{
				current_collection_table_no = this.current_spectrum_collection_table_no;
				current_collection_source_table = this.current_spectrum_collection_source_table;
				current_collection_target_table = this.current_spectrum_collection_target_table;
				current_collection_table_name = "spectrum_collection";
			}
			else
			{
				current_collection_table_no = this.current_hierarchy_collection_table_no;
				current_collection_source_table = this.current_hierarchy_collection_source_table;
				current_collection_target_table = this.current_hierarchy_collection_target_table;			
				current_collection_table_name = "hierarchy_collection";
			}
			
			
			
			EAVTableAndRelationsInfoStructure eav_info = new EAVTableAndRelationsInfoStructure(this.getEavServices().getEAVTableAndRelationsInfoStructure(metadata_level));
			
			String primary_key_name = eav_info.primary_id_name;


			for (QueryCondition cond : query.getEAVConditionFields())
			{
				EAVQueryConditionObject co = (EAVQueryConditionObject)cond;
				count_of_rows_in_target_table = 0;

				if(!co.isCondition_handled_at_hierarchy_level())
				{


					
					// build query for current cond
					ArrayList<String> tables = new ArrayList<String>();

					String eav_table_name = "eav";
					String primary_x_eav_table_name = eav_info.primary_x_eav_tablename;
					tables.add(eav_table_name);
					tables.add(primary_x_eav_table_name);



					String curr_cond;

					if(co.getFieldName().equals("spatial_val"))
					{
						curr_cond = SB.prefix(eav_table_name, "attribute_id") + " = " + getAttributes().get_attribute_id(co.getAttributeName()) +
								" and " + co.getOperator() + "( " + co.getStringValue() + ", " + SB.prefix(eav_table_name, co.getFieldName()) + ") = 1" +
								" and " + eav_table_name + ".eav_id = " + primary_x_eav_table_name + ".eav_id"
								;					

					}
					else
					{
						curr_cond = SB.prefix(eav_table_name, "attribute_id") + " = " + getAttributes().get_attribute_id(co.getAttributeName()) +
								" and " + SB.prefix(eav_table_name, co.getFieldName()) + " " + co.getOperator() + " " + 
								((co.QuoteValue())?SB.quote_string(co.getStringValue()):co.getStringValue()) +
								" and " + eav_table_name + ".eav_id = " + primary_x_eav_table_name + ".eav_id"
								;					
					}
					
					
					
//					String queryString = getStatementBuilder().assemble_sql_select_distinct_query(
//							SB.prefix(primary_x_eav_table_name, primary_key_name),
//							getStatementBuilder().conc_tables(tables),
//							curr_cond);
					
					
					

					if(iteration_result_exists && metadata_level == MetaParameter.SPECTRUM_LEVEL)
					{
						curr_cond = curr_cond + " and " + SB.prefix(primary_x_eav_table_name, primary_key_name) + " = " + current_collection_source_table + "." + primary_key_name;

						tables.add(current_collection_source_table);

					}

					

					String queryString = getStatementBuilder().assemble_sql_select_distinct_query(
							SB.prefix(primary_x_eav_table_name, primary_key_name),
							getStatementBuilder().conc_tables(tables),
							curr_cond);


					// hierarchies can only positively restrict; if a condition is not met, then it is checked again on spectrum level
					// if a hierarchy condition is met then it must be translated into the spectrum level
					// the reason for that is:
					// - AND selections cannot be done in a single query on hierarchy level in the case of nested hierarchies
					// - consequentially, the selections must be done iteratively on spectrum level.
					if(metadata_level == MetaParameter.HIERARCHY_LEVEL)
					{
						String countqueryString = getStatementBuilder().assemble_sql_select_query(
								"count(" + SB.prefix(primary_x_eav_table_name, primary_key_name) + ")",
								getStatementBuilder().conc_tables(tables),
								curr_cond);

						int count = 0;
						ResultSet rs = stmt.executeQuery(countqueryString);
						while (rs.next()) {
							count =rs.getInt(1);
						}
						rs.close();		

						if(count > 0)
						{
							// this a a positive restriction: enter hierarchy ids in temporary table						
							update_collection = true;

							// inform condition that it has been met on hierarchy level
							co.setCondition_handled_at_hierarchy_level(true);

							//iteration_result_exists = true; // true for all further queries
							

							//						}
							//						else
							//						{
							//							// not found; keep the current correction table by effecting a double switch
							//							//current_collection_table_no = (current_collection_table_no + 1) % 2;
							//							update_collection = false;
							//						}

							// insert into spectrum collection table if any matches were found on hierarchy level
							try {
								count = 0;

								String selectqueryString = getStatementBuilder().assemble_sql_select_query(
										SB.prefix(primary_x_eav_table_name, primary_key_name),
										getStatementBuilder().conc_tables(tables),
										curr_cond);


								String querycountString = "select count(spectrum_id) from hierarchy_level_x_spectrum where hierarchy_level_id in (" + selectqueryString + ")";
								queryString = "select spectrum_id from hierarchy_level_x_spectrum where hierarchy_level_id in (" + selectqueryString + ")";
								//queryString = "select count(spectrum_id) from hierarchy_level_x_spectrum where hierarchy_level_id in (select " + primary_key_name + " from " + current_collection_target_table + ")";		

								rs = stmt.executeQuery(querycountString.toString());
								while (rs.next()) {
									count =rs.getInt(1);
								}
								rs.close();		
								
								
								String delete_string = "delete from " + current_collection_target_table;
								stmt.executeUpdate(delete_string);		
								
								queryString = "insert into " + current_collection_target_table + "("+ primary_key_name + ") " + selectqueryString;
								stmt.executeUpdate(queryString);								

								if(count > 0)
								{
									
									tables = new ArrayList<String>();	

									tables.add("hierarchy_level_x_spectrum");	
									//tables.add(eav_table_name);

									//String cond_str = "hierarchy_level_id in (" + selectqueryString + ")";
									String cond_str = "hierarchy_level_id in (select " + primary_key_name + " from " + current_collection_target_table + ")";

									if(iteration_result_exists)
									{

										cond_str = cond_str + " and " + SB.prefix("hierarchy_level_x_spectrum", "spectrum_id") + " = " + this.current_spectrum_collection_source_table + "." + "spectrum_id";

										tables.add(this.current_spectrum_collection_source_table);

									}			


									String querySelectString = getStatementBuilder().assemble_sql_select_distinct_query(
											SB.prefix("hierarchy_level_x_spectrum","spectrum_id"),
											getStatementBuilder().conc_tables(tables),
											cond_str);	
									
									
									delete_string = "delete from " + this.current_spectrum_collection_target_table;
									stmt.executeUpdate(delete_string);										

									queryString = "insert into " + this.current_spectrum_collection_target_table + "(spectrum_id) " 
											+ querySelectString;
									//										+ "select distinct spectrum_id from hierarchy_level_x_spectrum where hierarchy_level_id in (select " + primary_key_name + " from " + current_collection_target_table + ")";
									stmt.executeUpdate(queryString);	
									
									// for debugging reasons ...							
									queryString = "select count(*) from " + this.current_spectrum_collection_target_table;
									
									count_of_rows_in_target_table = 0;
									rs = stmt.executeQuery(queryString);
									while (rs.next()) {
										count_of_rows_in_target_table =rs.getInt(1);
									}		
									rs.close();	

									this.current_spectrum_collection_table_no = (this.current_spectrum_collection_table_no + 1) % 2;
									this.current_spectrum_collection_source_table = this.current_spectrum_collection_target_table;
									this.current_spectrum_collection_target_table = SB.prefix(getTempDatabaseName(), "spectrum_collection" + this.current_spectrum_collection_table_no.toString());


									iteration_result_exists = true; // true for all further queries

								}
								// the iteration result status must not be changed here; in case the hierarchies did not turn up a result, the variable iteration_result_exists must remain unchanged
								//							else
								//								iteration_result_exists = false;

							}
							catch (SQLException ex) {
								ex.printStackTrace();
							}										

						}

					}


					// clear target temp table
					if(update_collection && metadata_level == MetaParameter.SPECTRUM_LEVEL)
					{
						if(metadata_level == MetaParameter.SPECTRUM_LEVEL)
						{
							String delete_string = "delete from " + current_collection_target_table;
							stmt.executeUpdate(delete_string);				
						}


						// put results into current target table
						queryString = "insert into " + current_collection_target_table + "("+ primary_key_name + ") " + queryString;
						stmt.executeUpdate(queryString);
						

						// for debugging reasons ...							
						queryString = "select count(*) from " + current_collection_target_table;
						
						count_of_rows_in_target_table = 0;
						ResultSet rs = stmt.executeQuery(queryString);
						while (rs.next()) {
							count_of_rows_in_target_table =rs.getInt(1);
						}		
						rs.close();	
//						
						if(count_of_rows_in_target_table > 0)
						{								
							iteration_result_exists = true; // true for all further queries
						}
							

					}

				}


				if(metadata_level == MetaParameter.SPECTRUM_LEVEL)
				{
					
					if(count_of_rows_in_target_table > 0)
					{
						// upon a successful restriction the source and target must switch again
						this.current_spectrum_collection_table_no = (this.current_spectrum_collection_table_no + 1) % 2;
						this.current_spectrum_collection_source_table = this.current_spectrum_collection_target_table;
						this.current_spectrum_collection_target_table = SB.prefix(getTempDatabaseName(), "spectrum_collection" + this.current_spectrum_collection_table_no.toString());
					}
					
				}
				else
				{
//					if(count_of_rows_in_target_table > 0)
//					{
//						// switch between source and target temp tables
//						current_collection_table_no = (current_collection_table_no + 1) % 2;
//						current_collection_source_table = current_collection_target_table;
//						current_collection_target_table = SB.prefix(getTempDatabaseName(), current_collection_table_name + current_collection_table_no.toString());						
//
//					}
					
				}


				// set fields in instance
				if(metadata_level == MetaParameter.SPECTRUM_LEVEL)
				{
//					this.current_spectrum_collection_table_no = current_collection_table_no;
//					this.current_spectrum_collection_source_table = current_collection_source_table;
//					this.current_spectrum_collection_target_table = current_collection_target_table;
				}
				else
				{
					this.current_hierarchy_collection_table_no = current_collection_table_no;
					this.current_hierarchy_collection_source_table = current_collection_source_table;
					this.current_hierarchy_collection_target_table = current_collection_target_table;			
				}		

			}
			
			
//			// convert hierarchy ids into spectrum ids
//			if(metadata_level == MetaParameter.HIERARCHY_LEVEL)
//			{
//
//				// insert into spectrum collection table if any matches were found on hierarchy level
//				try {
//					int count = 0;
//					String queryString = "select count(spectrum_id) from hierarchy_level_x_spectrum where hierarchy_level_id in (select " + primary_key_name + " from " + current_collection_target_table + ")";
//					ResultSet rs = stmt.executeQuery(queryString.toString());
//					while (rs.next()) {
//						count =rs.getInt(1);
//					}
//					rs.close();								
//
//					if(count > 0)
//					{
//						iteration_result_exists = true; // true for all further queries
//
//						queryString = "insert into " + this.current_spectrum_collection_target_table + "(spectrum_id) " 
//								+ "select distinct spectrum_id from hierarchy_level_x_spectrum where hierarchy_level_id in (select " + primary_key_name + " from " + current_collection_target_table + ")";
//						stmt.executeUpdate(queryString);			
//						
//						this.current_spectrum_collection_table_no = (this.current_spectrum_collection_table_no + 1) % 2;
//						this.current_spectrum_collection_source_table = this.current_spectrum_collection_target_table;
//						this.current_spectrum_collection_target_table = SB.prefix(getTempDatabaseName(), "spectrum_collection" + this.current_spectrum_collection_table_no.toString());
//						
//					}
//					// the iteration result status must not be changed here; in case the hierarchies did not turn up a result, the variable iteration_result_exists must remain unchanged
////					else
////						iteration_result_exists = false;
//
//				}
//				catch (SQLException ex) {
//					ex.printStackTrace();
//				}				
//
//			}
			
			stmt.close();

		}
		catch (SQLException ex) {
			// database error
			System.out.println(ex.toString());
			throw new SPECCHIOFactoryException(ex);
		}	


	}


	/**
	 * Get the identifiers of all spectra that match a given query.
	 * 
	 * @param query		the query
	 * 
	 * @return an array of identifiers
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */
	public ArrayList<Integer> getIdsMatchingQuery(Query query) throws SPECCHIOFactoryException {
		
		// sanity check
		if (!query.getQueryType().equals(Query.SELECT_QUERY)) {
			throw new SPECCHIOFactoryException("Not a select query.");
		}
		
		return getSpectraMatchingQuery(query);
		
	}
	
	

	/**
	 * Get the direct hierarchy id of a single spectrum
	 * 
	 * @param ids_d		container holding the identifier of the desired spectrum
	 * 
	 * @return hierarchy id
	 * 
	 */
	public int getDirectHierarchyOfSpectrum(ArrayList<Integer> spectrum_id) {
		
		ArrayList<Integer> ids = this.getEavServices().getDirectHierarchyIds(spectrum_id);
		
		
		
		
		// TODO Auto-generated method stub
		return ids.get(0);
	}	

	/**
	 * Get hierarchy ids, directly above these spectra
	 * 
	 * @param spectrum_ids		the identifiers of the desired spectra
	 * 
	 * @return hierarchy ids
	 * 
	 * @throws SPECCHIOFactoryException	
	 */	
	public ArrayList<Integer> getDirectHierarchyIds(ArrayList<Integer> spectrum_ids)
	{		
		return this.getEavServices().getDirectHierarchyIds(spectrum_ids);
	}
	
	
	/**
	 * Get a list of hierarchy ids, covering all hierarchies above these spectra
	 * 
	 * @param spectrum_ids		the identifiers of the desired spectra
	 * 
	 * @return hierarchy ids
	 * 
	 * @throws SPECCHIOFactoryException	
	 */	
	public List<Integer> getHierarchyIdsOfSpectra(ArrayList<Integer> spectrum_ids) {

		return this.getEavServices().getHierarchyIds(spectrum_ids);
	}	
	
	
	/**
	 * Get the pictures associated with a given spectrum.
	 * 
	 * @param spectrum_id	the spectrum identifier
	 * 
	 * @return a PictureTable object containing the picture data
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public PictureTable getPictures(int spectrum_id) throws SPECCHIOFactoryException {
		
		PictureTable pictures = new PictureTable();
		
		try {
			
			Statement stmt = getStatementBuilder().createStatement();
			String query = "select picture.picture_id,picture.caption,picture.image_data from spectrum_x_picture,picture " +
					"where picture.picture_id = spectrum_x_picture.picture_id " +
					"and spectrum_x_picture.spectrum_id = " + Integer.toString(spectrum_id);
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				Integer picture_id = rs.getInt(1);
				String caption = rs.getString(2);
				Blob image_data = rs.getBlob(3);
				Picture picture = new Picture(picture_id, spectrum_id, caption, image_data.getBytes(1, (int)image_data.length()));
				pictures.put(picture_id, picture);
			}
			rs.close();
			stmt.close();
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return pictures;
		
	}

	
	/**
	 * Get a spectrum object.
	 * 
	 * @param spectrum_id		the identifier of the desired spectrum
	 * @param prepare_metadata	load the spectrum's metadata?
	 * 
	 * @return a Spectrum object representing the desired spectrum
	 * 
	 * @throws SPECCHIOFactoryException	spectrum_id does not exist
	 */
	public Spectrum getSpectrum(int spectrum_id, boolean prepare_metadata) throws SPECCHIOFactoryException {
		
		Spectrum s = new Spectrum(spectrum_id);
		
		try {
			
			SQL_StatementBuilder SQL = getStatementBuilder();
			SpectrumQueryBank SQB = new SpectrumQueryBank(SQL);
			Statement stmt = getConnection().createStatement();
			String query;
			ResultSet rs;
			
			query = "select campaign_id, hierarchy_level_id from spectrum where spectrum_id = " + Integer.toString(spectrum_id);
			rs = stmt.executeQuery(query);
			while (rs.next()) {
				//s.setIsReference(new MetaDatatype<Boolean>("Is reference", rs.getBoolean(1)));
				s.setCampaignId(rs.getInt(1));
				s.setHierarchyLevelId(rs.getInt(2));
			}
			rs.close();
			
			String id_fieldnames[] = new String[Spectrum.METADATA_FIELDS.length];
			for (int i = 0; i < Spectrum.METADATA_FIELDS.length; i++) {
				id_fieldnames[i] = Spectrum.METADATA_FIELDS[i] + "_id";
			}
			query = "select "  + SQL.conc_cols(id_fieldnames) + " from spectrum where spectrum_id = " + Integer.toString(spectrum_id);
			rs = stmt.executeQuery(query);
			int row_cnt = 1;
			while (rs.next()) {
				for (String fieldname : Spectrum.METADATA_FIELDS) {
					s.setMetadataId(fieldname, rs.getInt(fieldname + "_id"));
				}
				
			}	
			rs.close();	
			
			// get EAV
			s.setMetadata(MF.getMetadataForSpectrum(spectrum_id));	
			
			if(prepare_metadata)
			{
				// set file name and comments from metadata
//				MetaParameter mp;
//				mp = s.getMetadata().get_first_entry("File Name");
//				if (mp != null) {
//					MetaDatatype<String> mds = new MetaDatatype<String>("File Name");
//					mds.set_value((String)mp.getValue());
//					s.setFileName(mds);
//				}
//				mp = s.getMetadata().get_first_entry("File Comments");
//				if (mp != null) {
//					MetaDatatype<String> mds = new MetaDatatype<String>("File Comments");
//					mds.set_value((String)mp.getValue());
//					s.setFileComment(mds);
//				}
//			
//				// get the capture date from date and time
//				s.setCaptureDate(new MetaDatatype<Date>("Capture Date", SQL.get_java_date_time(s.getCaptureDateTime())));		
//					
//				// get the loading date from date and time
//				s.setLoadingDate(new MetaDatatype<Date>("Loading Date", SQL.get_java_date_time(s.getLoadDateTime())));
//				
				
				ArrayList<Integer> id = new ArrayList<Integer>();
				id.add(spectrum_id);
				
				// file format
				s.setFileFormat(new MetaDatatype<String>("File Format"));
				if (s.getFileFormatId() != 0)
				{
					query = SQB.get_file_format_query(id);
					rs = stmt.executeQuery(query);
					while (rs.next()) {
						s.getFileFormat().set_value(rs.getString(1));
					}
					rs.close();
				}

				
				
				// quality levels
//				s.setRequiredQualityLevel(new MetaDatatype<String>("Required quality level"));
//				s.setAttainedQualityLevel(new MetaDatatype<String>("Attained quality level"));
//				if(s.getQualityLevelId() != 0)
//				{
//					query = SQB.get_quality_query(id);
//					
//					rs = stmt.executeQuery(query);
//					
//					while (rs.next()) {
//						s.getAttainedQualityLevel().set_value(rs.getString(1));
//					}
//					rs.close();						
//				}
				
//				if(s.getRequiredQualityLevelId() != 0)
//				{
//					query = SQB.get_required_quality_query(id);
//					
//					rs = stmt.executeQuery(query);
//					
//					while (rs.next()) {
//						s.getRequiredQualityLevel().set_value(rs.getString(1));
//					}
//					rs.close();						
//				}
				
				
				// create position attributes
//				s.setLatitude(new MetaDatatype<Double>("Latitude"));
//				s.setLongitude(new MetaDatatype<Double>("Longitude"));
//				s.setAltitude(new MetaDatatype<Double>("Altitude"));	
//				s.setLocationName(new MetaDatatype<String>("Location"));
//				
//				if(s.getPositionId() != 0)
//				{
//					query = SQB.get_position_query(id);				
//					rs = stmt.executeQuery(query);
//					
//					while (rs.next()) {
//						s.getLatitude().value = rs.getDouble(1);
//						s.getLongitude().value = rs.getDouble(2);
//						s.getAltitude().value = rs.getDouble(3);
//						s.getLocationName().value = rs.getString(4);
//					}
//					rs.close();						
//				}
				
				
				if(s.getSensorId() != 0)
					s.setSensor(getDataCache().get_sensor(s.getSensorId()));
				else
					s.setSensor(new Sensor()); // dummy sensor
					
				
				// campaign
//				s.setCampaignName(new MetaDatatype<String>("Campaign name"));
//				s.setCampaignDescription(new MetaDatatype<String>("Campaign desc"));
				
//				if(s.getCampaignId() != 0)
//				{
//					query = SQB.get_campaign_query(id);				
//					rs = stmt.executeQuery(query);
//					
//					while (rs.next()) {
//						s.getCampaignName().value = rs.getString(1);
//						s.getCampaignDescription().value = rs.getString(2);
//					}
//					rs.close();						
//				}		
				
				// landcover
//				s.setLandcover(new MetaDatatype<String>("Landcover"));
//				if(s.getLandcoverId() != 0)
//				{
//					query = SQB.get_landcover_query(id);				
//					rs = stmt.executeQuery(query);
//					
//					while (rs.next()) {
//						s.getLandcover().value = rs.getString(1);
//					}
//					rs.close();						
//				}		
				
				// environ conds
//				s.setCloudCover(new MetaDatatype<String>("Cloud cover [octas]"));
//				s.setAmbientTemperature(new MetaDatatype<String>("Ambient temp. [�C]"));
//				s.setAirPressure(new MetaDatatype<String>("Air pressure"));
//				s.setRelativeHumidity(new MetaDatatype<String>("Rel. humidity"));
//				s.setWindDirection(new MetaDatatype<String>("Wind direction"));
//				s.setWindSpeed(new MetaDatatype<String>("Wind speed"));
//				if(s.getEnvironmentalConditionId() != 0)
//				{
//					query = SQB.get_env_cond_query(spectrum_id);				
//					rs = stmt.executeQuery(query);
//					row_cnt = 1;
//					while (rs.next()) {				
//						s.getAmbientTemperature().value = rs.getString(row_cnt++);
//						s.getAirPressure().value = rs.getString(row_cnt++);
//						s.getRelativeHumidity().value = rs.getString(row_cnt++);
//						s.getCloudCover().value = rs.getString(row_cnt++);
//						s.getWindDirection().value = rs.getString(row_cnt++);
//						s.getWindSpeed().value = rs.getString(row_cnt++);
//					}
//					rs.close();						
//				}		
				
				
				// geometry
//				s.setSensorZenith(new MetaDatatype<String>("Sensor zenith"));
//				s.setSensorAzimuth(new MetaDatatype<String>("Sensor azimuth"));
//				s.setIlluminationZenith(new MetaDatatype<String>("Illumination zenith"));
//				s.setIlluminationAzimuth(new MetaDatatype<String>("Illumination azimuth"));
//				s.setSensorDistance(new MetaDatatype<String>("Sensor distance"));
//				s.setIlluminationDistance(new MetaDatatype<String>("Illumination distance"));		
//				if(s.getSamplingGeometryId() != 0)
//				{
//					query = SQB.get_geometry_query(id);				
//					rs = stmt.executeQuery(query);
//					row_cnt = 1;
//					while (rs.next()) {
//						s.getSensorZenith().value = rs.getString(row_cnt++);
//						s.getSensorAzimuth().value = rs.getString(row_cnt++);
//						s.getIlluminationZenith().value = rs.getString(row_cnt++);
//						s.getIlluminationAzimuth().value = rs.getString(row_cnt++);
//						s.getSensorDistance().value = rs.getString(row_cnt++);
//						s.getIlluminationDistance().value = rs.getString(row_cnt++);
//					}
//					rs.close();						
//				}		
				
				// measurement_unit
				s.setMeasurementUnit(new MetaDatatype<String>("Measurement unit"));		
				if(s.getMeasurementUnitId() != 0)
				{
					query = SQB.get_measurement_unit_query(id);				
					rs = stmt.executeQuery(query);
					row_cnt = 1;
					while (rs.next()) {
						s.getMeasurementUnit().value = rs.getString(row_cnt++);
					}
					rs.close();						
				}		
				
				// measurement_type
//				s.setMeasurementType(new MetaDatatype<String>("Beam geometry"));		
//				if(s.getMeasurementTypeId() != 0)
//				{
//					query = SQB.get_measurement_type_query(id);				
//					rs = stmt.executeQuery(query);
//					row_cnt = 1;
//					while (rs.next()) {
//						s.getMeasurementType().value = rs.getString(row_cnt++);
//					}
//					rs.close();						
//				}				
//		
				// illumination_source
//				s.setIlluminationSource(new MetaDatatype<String>("Illumination source"));		
//				if(s.getIlluminationSourceId() != 0)
//				{
//					query = SQB.get_illumination_source_query(id);				
//					rs = stmt.executeQuery(query);
//					row_cnt = 1;
//					while (rs.next()) {
//						s.getIlluminationSource().value = rs.getString(row_cnt++);
//					}
//					rs.close();						
//				}				
		
				// sampling_environment
//				s.setSamplingEnvironment(new MetaDatatype<String>("Sampling environment"));		
//				if(s.getSamplingEnvironmentId() != 0)
//				{
//					query = SQB.get_sampling_environment_query(id);				
//					rs = stmt.executeQuery(query);
//					row_cnt = 1;
//					while (rs.next()) {
//						s.getSamplingEnvironment().value = rs.getString(row_cnt++);
//					}
//					rs.close();						
//				}
				
				
				// foreoptic
//				s.setForeoptic(new MetaDatatype<String>("FOV [degrees]"));		
//				if(s.getForeopticId() != 0)
//				{
//					query = SQB.get_foreoptic_query(id);				
//					rs = stmt.executeQuery(query);
//					row_cnt = 1;
//					while (rs.next()) {
//						s.getForeoptic().value = rs.getString(row_cnt++);
//					}
//					rs.close();						
//				}	
				
				if(s.getInstrumentId() != 0)
				{					
					s.setInstrument(getDataCache().get_instrument(s.getInstrumentId(), s.getCalibrationId()));
				}
				else
				{
					s.setInstrument(new Instrument()); // dummy instrument, only existing to provide empty attributes
													// for reports
					s.getInstrument().setSensor(s.getSensor()); // set sensor so we can have a uniform call during the get_channels
														// when plotting
				}
			
			}
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		catch (NoSuchMethodException ex) {
			// bad field name in Spectrum.METADATA_FIELDS; should never happen
			throw new SPECCHIOFactoryException(ex);
		}
			
		return s;
			
	}
	
	
	/**
	 * Get the spectrum factor table.
	 * 
	 * @param spectrum_ids		the spectrum identifiers to include in the table
	 * @param calibration_ids	the calibration identifiers to include in the table
	 * 
	 * @return a table mapping spectra to factors
	 * 
	 * @throws SPECCHIOFactoryException database error
	 */
	public SpectrumFactorTable getSpectrumFactorTable(
			ArrayList<Integer> spectrum_ids,
			ArrayList<Integer> calibration_ids
		) throws SPECCHIOFactoryException {
		
		SpectrumFactorTable table = new SpectrumFactorTable();

		try {
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query = "select s.spectrum_id, c.cal_factors from spectrum s, calibration c, reference r where " +
					"spectrum_id in (" + SQL.conc_ids(spectrum_ids) + ") and c.reference_id = r. reference_id " +
					"and c.cal_factors in ("+ SQL.conc_ids(calibration_ids) + ")";
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) 
			{
				table.put(rs.getInt(1), rs.getInt(2));
			}
			rs.close();
			stmt.close();
		} catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return table;
	}
	
	
	/**
	 * Get the number of spectra in database
	 * 
	 * @param String		empty string
	 * 
	 * @return the number of spectra in the database
	 * 
	 * @throws SPECCHIOFactoryException	could not access the database
	 */	
	public int getSpectrumCountInDB() {
		
		int count = 0;
		
		try {
			
			
			
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			
			String query = "select count(*) from spectrum";
			
			ResultSet rs = stmt.executeQuery(query);
			
			while (rs.next()) {				
				count = rs.getInt(1);
			}
			rs.close();
			stmt.close();
			

		} catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
		return count;
	}
	
	
	
	/**
	 * Get the spectrum data links that refer to a given target and/or reference.
	 * 
	 * @param target_ids	the identifiers of the target spectra (null or empty to match all targets)
	 * @param reference_ids	the identifiers of the reference spectra (null or empty all references)
	 * @param is_admin		perform the query as the admin user
	 * 
	 * @return an array of identifiers of linked spectra
	 * 
	 * @throws SPECCHIOFactoryException	database error
	 */
	public SpectrumDataLink[] getTargetReferenceLinks(ArrayList<Integer> target_ids, ArrayList<Integer> reference_ids, boolean is_admin) throws SPECCHIOFactoryException {
		
		try {
			// work out the correct table name
//			String tablename = (is_admin)? "spectrum_datalink" : "spectrum_datalink_view";
			
			// create SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			
			// build a query that will return all matching datalinks
			String query = new String();
//			query.append("select spectrum_id, linked_spectrum_id, name");
//			query.append(" from " + tablename + ", datalink_type ");
//			query.append(" where " + SQL.prefix(tablename, "datalink_type_id") + "=" + SQL.prefix("datalink_type", "datalink_type_id"));
//			if (target_ids != null && target_ids.size() > 0) {
//				query.append(" and spectrum_id in " + SQL.quote_list(target_ids));
//			}
//			if (reference_ids != null && reference_ids.size() > 0) {
//				query.append(" and linked_spectrum_id in " + SQL.quote_list(reference_ids));
//			}
			
			ArrayList<Integer> all_ids = null;
			if(target_ids != null)
			{
				all_ids = target_ids;
				if(reference_ids != null) all_ids.addAll(reference_ids);
			}
			else
			{
				all_ids = reference_ids;
			}
			
			query = "select eav.spectrum_id, sxe.spectrum_id, attribute_id, eav.eav_id from eav eav, spectrum_x_eav sxe where sxe.spectrum_id in " + SQL.quote_list(all_ids) + " and sxe.eav_id = eav.eav_id and eav.attribute_id in (" + this.MF.getAttributes().get_attribute_id("Target Data Link") +
					"," + this.MF.getAttributes().get_attribute_id("Reference Data Link") + ")";
			
			
			
			// build a list of matching datalinks
			ArrayList<SpectrumDataLink> datalinks = new ArrayList<SpectrumDataLink>();
			
			if(all_ids.size()>0)
			{			
				ResultSet rs = stmt.executeQuery(query.toString());
				while (rs.next()) {				
					int referenced_spectrum_id = rs.getInt(1);
					int referencing_spectrum_id = rs.getInt(2);
					int attr_id = rs.getInt(3);
					int eav_id = rs.getInt(4);
					datalinks.add(new SpectrumDataLink(eav_id, referencing_spectrum_id, referenced_spectrum_id, MF.getAttributes().get_attribute_info(attr_id).name));
				}
				rs.close();
				stmt.close();
			}
			
			// convert the list into an array
			return datalinks.toArray(new SpectrumDataLink[datalinks.size()]);
			
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		}
		
	}
	
	
	
	/**
	 * Insert links from a target to a set of references.
	 * 
	 * @param target_id						the identifier of the target node
	 * @param reference_ids	the 			identifiers of the reference nodes
	 * @param link_to_closest_on_timeline	Link to closest reference spectrum based on the acquisition times
	 * 
	 * @return the number of links created
	 * 
	 * @throws IllegalArgumentException	this target cannot be linked to any of the proposed references
	 * @throws SPECCHIOFactoryException	database error
	 */
	public int insertTargetReferenceLinks(Integer target_id, ArrayList<Integer> reference_ids, boolean link_to_closest_on_timeline) throws IllegalArgumentException, SPECCHIOFactoryException {
		
		int num = 0;
		
		try {
			// set up SQL-building objects
			SQL_StatementBuilder SQL = getStatementBuilder();
			Statement stmt = SQL.createStatement();
			String query;
			
			// check that the request makes sense
			SpectrumDataLink links[];
			if (reference_ids.contains(target_id)) {
				// trying to link target to itself
				throw new IllegalArgumentException("Cannot link a target to itself.");
			}
			ArrayList<Integer> target_id_list = new ArrayList<Integer>(1);
			target_id_list.add(target_id);
			links = getTargetReferenceLinks(target_id_list, null, false);
			if (links.length > 0) {
				// target already has a reference
				throw new IllegalArgumentException("Target " + links[0].getReferencingId() + " is already linked to reference " + links[0].getReferencedId());
			}
			links = getTargetReferenceLinks(null, target_id_list, false);
			if (links.length > 0) {
				// target is already a reference
				throw new IllegalArgumentException("The proposed target " + target_id + " is in use as a reference.");
			}
			
			
			ArrayList<Integer> reference_ids_ = new ArrayList<Integer>();

			// get target acquisition time for easier query formulation below
			ArrayList<Integer> time_ids = this.getEavServices().get_eav_ids(MetaParameter.SPECTRUM_LEVEL, target_id, "Acquisition Time");
			MetaDate target_time = (MetaDate) this.getEavServices().load_metaparameter(time_ids.get(0));
			
			String target_time_str = MetaDate.formatDate((DateTime)target_time.getValue()); // ensure we are getting the time stamp correctly formatted as inserted into the database

			// get corresponding reference spectrum id
			if (link_to_closest_on_timeline)
			{
				int reference_id = 0;
				//int ASD_coding = 0;
				query = "select refs.spectrum_id, ASD_coding from spectrum refs " + 
						"left outer join spectrum t on t.measurement_unit_id = refs.measurement_unit_id, spectrum_x_eav sxe, eav, measurement_unit mu " +
						"where refs.spectrum_id in (" + SQL.conc_ids(reference_ids) + ")" + " and t.spectrum_id = " + target_id +
						" and refs.spectrum_id = sxe.spectrum_id and eav.eav_id = sxe.eav_id and eav.attribute_id = (select attribute_id from attribute where name = 'Acquisition Time') and mu.measurement_unit_id = t.measurement_unit_id order by abs(timeDIFF(eav.datetime_val, '" + target_time_str + "')) limit 1";
				ResultSet rs = stmt.executeQuery(query);
				if (rs.next()) {
					reference_id = rs.getInt(1);	
					//ASD_coding = rs.getInt(2);
					reference_ids_.add(reference_id);
				}			
				rs.close();	
				
			}
			else
			{
				reference_ids_.addAll(reference_ids);
			}
			
			// work out data link name
//			String datalink_name = "";
//			if(ASD_coding == 2) {
//				datalink_name = "Spectralon data";
//			}
//			if(ASD_coding == 4) {
//				datalink_name = "Cosine receptor data";
//			}		
			
			// only create datalink if we found a reference and a link type
			//if(reference_id != 0 && datalink_name.length() > 0)
			if(reference_ids_.size() > 0)
			{
				// create a new spectrum datalink entry 
//				query = "insert into spectrum_datalink_view (spectrum_id, linked_spectrum_id, datalink_type_id) " +
//						"values (" + SQL.conc_ids(target_id, reference_id)  + ", " +
//						"(select datalink_type_id from datalink_type where name = '" + datalink_name + "'))";
//				stmt.executeUpdate(query); // execute update
				num = 1;
				
				for(Integer reference_id : reference_ids_)
				{
					
					// insert eav for target and reference
					// link target to reference
					MetaParameter mp = MetaParameter.newInstance(getAttributes().get_attribute_info("Target Data Link", "Data Links"));
					mp.setValue(target_id);
					
//					Integer[] spectrum_id_array = new Integer[1];
//					spectrum_id_array[0]=reference_id;
					ArrayList<Integer> ids = new ArrayList<Integer>();
					ids.add(reference_id);
					
					MF.updateMetadata(mp, ids);
					
					// link reference to target
					mp = MetaParameter.newInstance(getAttributes().get_attribute_info("Reference Data Link", "Data Links"));
					mp.setValue(reference_id);
					
					ids.clear();
					ids.add(target_id);
					
					MF.updateMetadata(mp, ids);
					
					num++;
				}
			}
			
			stmt.close();
			
		}
		catch (SQLException ex) {
			// database error
			throw new SPECCHIOFactoryException(ex);
		} catch (MetaParameterFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return num;
		
	}
	
	
	public AVMatchingListCollection sortByAttributes(AVMatchingList av_list) throws SPECCHIOFactoryException {
	
		EAVDBServices eav = getEavServices();
		AVSorter avs = new AVSorter(eav);
		avs.insert_into_lists(av_list);
		
		return avs.getAVMatchingListCollection();
	}
	
	
//	/**
//	 * Remove a spectrum from the database.
//	 * 
//	 * @param spectrum_id	the identifier of the spectrum to be removed
//	 * @param is_admin		is the requesting user an administator?
//	 * 
//	 * @throws SPECCHIOFactoryException	the spectrum could not be removed
//	 */
//	public void removeSpectrum(int spectrum_id, boolean is_admin) throws SPECCHIOFactoryException {
//		
//		try {
//			
//			Statement stmt = getStatementBuilder().createStatement();
//			String table_name;
//			String cmd;
//			
//			// remove datalinks
//			table_name = (is_admin)? "spectrum_datalink" : "spectrum_datalink_view";
//			cmd = "delete from "+table_name+" where " +
//			"spectrum_id = " + Integer.toString(spectrum_id) + " OR linked_spectrum_id = " + Integer.toString(spectrum_id);	
//			stmt.executeUpdate(cmd); 
//			
//			// EAV
//			// remove entries from eav x table
//			table_name = (is_admin)? "spectrum_x_eav" : "spectrum_x_eav_view";
//			cmd = "delete from "+table_name+" where " +
//			"spectrum_id = " + Integer.toString(spectrum_id);		
//			stmt.executeUpdate(cmd); 	
//			
//			String spectrum_x_eav_table_or_view = table_name;
//	
//			// remove zombie eav
//			table_name = (is_admin)? "eav" : "eav_view";
//			
//			cmd = "delete from "+table_name+" where eav_id not in (select eav_id from " +spectrum_x_eav_table_or_view+");";
//			stmt.executeUpdate(cmd);					
//			
//			// remove entries from hierarchy_level_x_spectrum
//			table_name = (is_admin)? "hierarchy_level_x_spectrum" : "hierarchy_level_x_spectrum_view";
//			cmd = "delete from "+table_name+" where " +
//			"spectrum_id = " + Integer.toString(spectrum_id);		
//			stmt.executeUpdate(cmd); 				
//					
//			// remove spectrum itself
//			table_name = (is_admin)? "spectrum" : "spectrum_view";
//			cmd = "delete from "+table_name+" where spectrum_id = " + Integer.toString(spectrum_id);
//			stmt.executeUpdate(cmd);
//			
//			stmt.close();
//		}
//		catch (SQLException ex) {
//			// bad SQL
//			throw new SPECCHIOFactoryException(ex);
//		}
//					
//	}
	
	/**
	 * Remove a spectrum from the database.
	 * 
	 * @param spectrum_ids	list of the identifier of the spectra to be removed
	 * @param is_admin		is the requesting user an administator?
	 * 
	 * @throws SPECCHIOFactoryException	the spectrum could not be removed
	 */
	public void removeSpectra(ArrayList<Integer> spectrum_ids, boolean is_admin) throws SPECCHIOFactoryException {
		
		try {
			
			Statement stmt = getStatementBuilder().createStatement();
			String table_name;
			String cmd;
			
			if(spectrum_ids.size() > 0)
			{

				String ids = getStatementBuilder().conc_ids(spectrum_ids);

				// remove datalinks: this is obsolete with newer databases
				table_name = (is_admin)? "spectrum_datalink" : "spectrum_datalink_view";
				cmd = "delete from "+table_name+" where " +
						"spectrum_id in (" + ids + ") OR linked_spectrum_id in (" + ids + ")";	
				try{
				stmt.executeUpdate(cmd); 
				} catch (SQLException ex)
				{
					// ignore the datalink error: newer databases do not have that table anymore!
					// SQLException msg = ex;
				}
				
				// get eav_ids of the datalinks: -> why is this actually needed and not part of the general EAV deletion?!?
				String query = "select eav_id from eav where spectrum_id in (" + ids + ")"; // restricting by attribute id not needed as only data links have the spectrum_id field filled.
				ArrayList<Integer> eav_ids = new ArrayList<Integer>();
				
				ResultSet rs = stmt.executeQuery(query);
				while (rs.next()) {
					eav_ids.add(rs.getInt(1));	
				}			
				rs.close();	
				
				
				table_name = (is_admin)? "spectrum_x_eav" : "spectrum_x_eav_view";
				cmd = "delete from "+table_name+" where " +
						"eav_id in (" + getStatementBuilder().conc_ids(eav_ids) + ")";	
				stmt.executeUpdate(cmd); 		
				
				table_name = (is_admin)? "eav" : "eav_view";
				cmd = "delete from "+table_name+" where " +
						"eav_id in (" + getStatementBuilder().conc_ids(eav_ids) + ")";	
				stmt.executeUpdate(cmd); 
				

				// EAV
				// remove entries from eav x table
				eav_ids.clear();
				query = "select eav_id from spectrum_x_eav where spectrum_id in (" + ids + ")"; 
				
				rs = stmt.executeQuery(query);
				while (rs.next()) {
					eav_ids.add(rs.getInt(1));	
				}			
				rs.close();	
				
				
				table_name = (is_admin)? "spectrum_x_eav" : "spectrum_x_eav_view";
				cmd = "delete from "+table_name+" where " +
						"spectrum_id in (" + ids + ")";	
				stmt.executeUpdate(cmd); 	
				
				// remove eav's that are no longer referenced
				table_name = (is_admin)? "eav" : "eav_view";
				
				// get eav_ids that are no longer referenced by other spectra
				ArrayList<Integer> eav_ids_to_delete = new ArrayList<Integer>();
				query = "    select eav.eav_id, count(sxe.spectrum_id) from eav eav LEFT JOIN spectrum_x_eav sxe" + 
						"     ON sxe.eav_id = eav.eav_id where eav.eav_id in (" + getStatementBuilder().conc_ids(eav_ids) + ")  group by eav_id;"; 
				
				rs = stmt.executeQuery(query);
				while (rs.next()) {
					
					int cnt = rs.getInt(2);
					if(cnt == 0) eav_ids_to_delete.add(rs.getInt(1));	
				}			
				rs.close();	
				
				
				cmd = "delete from "+table_name+" where " +
						"eav_id in (" + getStatementBuilder().conc_ids(eav_ids_to_delete) + ")";	
				stmt.executeUpdate(cmd); 						

//				String spectrum_x_eav_table_or_view = table_name;

				// remove zombie eav: no longer appropriate due to metadata at hierarchy level
//				table_name = (is_admin)? "eav" : "eav_view";
//
//				cmd = "delete from "+table_name+" where eav_id not in (select eav_id from " +spectrum_x_eav_table_or_view+");";
//				stmt.executeUpdate(cmd);					

				// remove entries from hierarchy_level_x_spectrum
				table_name = (is_admin)? "hierarchy_level_x_spectrum" : "hierarchy_level_x_spectrum_view";
				cmd = "delete from "+table_name+" where " +
						"spectrum_id in (" + ids + ")";		
				stmt.executeUpdate(cmd); 				

				// remove spectrum itself
				table_name = (is_admin)? "spectrum" : "spectrum_view";
				cmd = "delete from "+table_name+" where spectrum_id in (" + ids + ")";	
				stmt.executeUpdate(cmd);

				stmt.close();
			}
		}
		catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
					
	}	
	
	
	/**
	 * Update a metadata field for a given list of spectra.
	 * 
	 * @param ids		the identifiers of the spectra to be updated
	 * @param field		the field to be updated
	 * @param value		the new value for the field
	 * @param is_admin	is the user an administrator?
	 * 
	 * @throws SPECCHIOFactoryException	could not connect to the database
	 */
	public void updateMetadata(Integer[] ids, String field, Integer value, boolean is_admin) throws SPECCHIOFactoryException {
		
		try {
			
			Statement stmt = getStatementBuilder().createStatement();
			String valueString = (value != 0)? value.toString() : "null";
			String query = getStatementBuilder().assemble_sql_update_query(
					field + "_id" + "=" + valueString,
					(is_admin)? "spectrum" : "spectrum_view",
					"spectrum_id in (" + getStatementBuilder().conc_ids(ids) + ")"
				);
			stmt.executeUpdate(query);
			stmt.close();
			
		} catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		}
		
	}

	
	/**
	 * Update the spectral vector of a spectrum
	 * 
	 * @param spectrum	the file to be inserted
	 * 
	 * @throws SPECCHIOClientException
	 */
	public void updateSpectrumVector(Spectrum s)  throws SPECCHIOFactoryException {
		
		SQL_StatementBuilder SQL = getStatementBuilder();

		try {

			// insert the measurement blob
			String update_stm = "UPDATE " + (this.Is_admin()?"spectrum":"spectrum_view") + " set measurement = ? where spectrum_id = "
					+ s.getSpectrumId();
			PreparedStatement statement = SQL.prepareStatement(update_stm);

			
			byte[] temp_buf;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutput dos = new DataOutputStream(baos);
			
			for (int i = 0; i < s.getMeasurementVector().length; i++) {
				try {
					dos.writeFloat(s.getMeasurementVector()[i]);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			temp_buf = baos.toByteArray();
	
			InputStream vector = new ByteArrayInputStream(temp_buf);
				
			statement.setBinaryStream(1, vector, s.getMeasurementVector().length * 4);
			statement.executeUpdate();

			vector.close();


		} catch (SQLException ex) {
			// bad SQL
			throw new SPECCHIOFactoryException(ex);
		} catch (IOException ex) {
			// TODO Auto-generated catch block
			throw new SPECCHIOFactoryException(ex);
		}		

		
	}


	public List<Integer> getDirectSpectrumIdsOfHierarchy(int hierarchy_id) {
		
		
		ArrayList<Integer> ids = new ArrayList<Integer>();	
		
		try {
			Statement stmt = getStatementBuilder().createStatement();
		
			String query = "select spectrum.spectrum_id " +
					"from spectrum spectrum " +
					"where spectrum.hierarchy_level_id = " + hierarchy_id;
			
			ResultSet rs = stmt.executeQuery(query);
			while (rs.next()) {
				ids.add(rs.getInt(1));
			}
			rs.close();							
		
			stmt.close();			
			
		}
		catch (SQLException ex) {
			// database error
			System.out.println(ex.toString());
			throw new SPECCHIOFactoryException(ex);
		}			
		
		return ids;

	}







}
