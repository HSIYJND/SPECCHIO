package ch.specchio.eav_db;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.TimeZone;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import ch.specchio.types.MetaDate;


// uses the Singleton Pattern
public class SQL_StatementBuilder {

	private Connection conn;
	private DatabaseMetaData dbm;
	private Statement stmt;
	private Hashtable<Character, String> escapeSequences;

   public SQL_StatementBuilder(Connection conn) {
	   
	   // save connection and statement for later
	   try {
		   this.conn = conn;
		   //conn.setAutoCommit(false);
		   this.stmt = this.conn.createStatement();
		   this.dbm = conn.getMetaData();
	   }
	   catch (SQLException ex) {
		   // don't know why this would happen
		   ex.printStackTrace();
	   }
	   
	   // define escape sequences for quoting strings
	   escapeSequences = new Hashtable<Character, String>();
	   escapeSequences.put('\000', "\\0");
	   escapeSequences.put('\'', "\'\'");
	   escapeSequences.put('\"', "\\\"");
	   escapeSequences.put('\b', "\\b");
	   escapeSequences.put('\n', "\\n");
	   escapeSequences.put('\r', "\\r");
	   escapeSequences.put('\t', "\\t");
	   escapeSequences.put('\032', "\\Z");
	   escapeSequences.put('\\', "\\\\");
	   
   }
   
   public Blob createBlob() throws SQLException {
	   
	   return this.conn.createBlob();
	   
   }
   
   
   public Statement createStatement() throws SQLException {
	   
	   return this.conn.createStatement();
	   
   }
   
   
   public PreparedStatement prepareStatement(String statement) throws SQLException {
	   
	   return conn.prepareStatement(statement);
	   
   }
   
   
   public PreparedStatement prepareStatement(String statement, int autoGeneratedKeys) throws SQLException {
	   
	   return conn.prepareStatement(statement, autoGeneratedKeys);
	   
   }
   
   
   public synchronized String conc_cond(String conds, String new_cond)
   {
	   return conc_cond(conds, new_cond, "AND");	   
   }
   
   public synchronized String conc_cond(String conds, String new_cond, String op)
   {
	   if(conds.equals(""))
		   return new_cond;
	   
	   if(new_cond.equals(""))
		   return conds;
	   else
		   return conds.concat(" " + op + " " + new_cond);   
   }
   
   public synchronized String conc_attributes(String[] attr)
   {
	   // actually exactly the same like concatenating tables
	   return conc_tables(attr);
   }
   
   public synchronized String conc_attributes(ArrayList<String> attr)
   {
	   // actually exactly the same like concatenating tables
	   return conc_tables(attr);
   }
   
   // returns a concatenated string with all values quotes 
   // useful for insert statements
   synchronized public String conc_values(String... vals)
   {
	   StringBuilder builder = new StringBuilder();
	   builder.append(quote_string(vals[0]));
	   	   
	   for(int i=1; i < vals.length; i++)
	   {
	   		builder.append(", ");
	   		builder.append(quote_string(vals[i]));
	   }
	   return builder.toString();	  
		   
   }
   
   synchronized public String conc_values(ArrayList<String> vals)
   {
	   return conc_values(vals, true); 
   }
   
   synchronized public String conc_values(ArrayList<String> vals, boolean do_quote)
   {
	   
	   StringBuilder builder = new StringBuilder();
	   
	   if(vals.size() == 0)
	   {
		   builder.append("");
	   }
	   else
	   {
		  builder.append(do_quote ? quote_string(vals.get(0)) : vals.get(0));
	   }
	   	   
	   for(int i=1; i < vals.size(); i++)
	   {
	   		builder.append(", ");
	   		builder.append(do_quote ? quote_string(vals.get(i)) : vals.get(i));
	   }
	   return builder.toString();	  	   
	   
//	   String res;
//	   if(vals.size() == 0)
//	   {
//		   res = "";
//	   }
//	   else
//	   {
//		  res = (do_quote ? quote_string(vals.get(0)) : vals.get(0));
//	   }
//	   
//	   
//	   for(int i=1; i < vals.size(); i++)
//		   res = res.concat(", "+ (do_quote ? quote_string(vals.get(i)) : vals.get(i)));
//	   return res;	    
   }
   
   synchronized public String conc_values_for_multiple_insert(ArrayList<String> vals)
   {
	   
	   String res;
	   if(vals.size() == 0)
	   {
		   res = "";
	   }
	   else
	   {
		   res = "(" + quote_string(vals.get(0)) + ")";
	   }
	   
	   
	   for(int i=1; i < vals.size(); i++)
		   res = res + ", ("+quote_string(vals.get(i)) + ")";
	   return res;	    
   }   
   
   
   public synchronized String conc_ids(ArrayList<Integer> ids)
   {
	   StringBuilder builder = new StringBuilder();
	   if(ids.size() == 0)
	   {
		   builder.append("null");
	   }
	   else
	   {
		   builder.append(ids.get(0));
	   }
	   
	   
	   for(int i=1; i < ids.size(); i++)
	   {
	   		builder.append(", ");
	   		builder.append(ids.get(i));
	   }
	   return builder.toString();	  
   }   
   
   
   public synchronized String conc_ids(int... ids)
   {

	   //System.out.println("deprecated conc_ids call");
	   StringBuilder builder = new StringBuilder();
	   builder.append(ids[0]);
	   
	   for(int i=1; i < ids.length; i++)
	   {
	   		builder.append(", ");
	   		builder.append(ids[i]);
	   }
		   
	   return builder.toString();	   
   }
   
   
   public synchronized String conc_ids(Integer[] ids)
   {
	   StringBuilder builder = new StringBuilder();
	   builder.append(ids[0]);
	   
	   for(int i=1; i < ids.length; i++)
	   {
	   		builder.append(", ");
	   		builder.append(ids[i]);
	   }
		   
	   return builder.toString();	   
   }
   
   // join the two strings with a comma separation, but only if first string is not empty
   public synchronized String comma_conc_strings(String str1, String str2)
   {
		if(str1.length() > 0)
			str1+=", ";
		str1+=str2;
		
		return str1;
   }
   
   public synchronized String quote_identifier(String id)
   {
	   return "`" + id + "`";
   }
   
   public synchronized String quote_value(Object value) throws SQLException
   {
	   if (value == null) {
		   return "null";
	   } else if (value.getClass().isArray()) {
		   return quote_array((Object[])value);
	   } else if (value instanceof List<?>) {
		   return quote_list((List<?>)value);
	   } else if (value instanceof String) {
		   return quote_string((String) value);
	   } else if (value instanceof Integer || value instanceof Long) {
		   return value.toString();
	   } else if (value instanceof Double || value instanceof Float) {
		   return value.toString();
	   } else if (value instanceof Date) {
		   return quote_string(DateAsString((Date)value));
	   } else if (value instanceof DateTime) {
		   return quote_string(JodaDateAsString((DateTime)value));		   
	   } else {
		   throw new SQLException("Cannot represent object of type " + value.getClass() + " as an SQL value.");
	   }   
   }
   
   public synchronized String get_pstatement_value(Object value) throws SQLException
   {
	   if (value == null) {
		   return "null";
	   } else if (value.getClass().isArray()) {
		   return quote_array((Object[])value);
	   } else if (value instanceof List<?>) {
		   return quote_list((List<?>)value);
	   } else if (value instanceof String) {
		   return (String)value;
	   } else if (value instanceof Integer || value instanceof Long) {
		   return value.toString();
	   } else if (value instanceof Double || value instanceof Float) {
		   return value.toString();
	   } else if (value instanceof Date) {
		   return DateAsString((Date)value);
	   } else if (value instanceof DateTime) {
		   return JodaDateAsString((DateTime)value);		   
	   } else {
		   throw new SQLException("Cannot represent object of type " + value.getClass() + " as an SQL value.");
	   }   
   }
   
   
   public synchronized String quote_array(Object[] values) throws SQLException
   {
	   StringBuffer sbuf = new StringBuffer();
	   sbuf.append("(");
	   for (Object value : values) {
		   if (sbuf.length() > 1) {
			   sbuf.append(",");
		   }
		   sbuf.append(quote_value(value));
	   }
	   sbuf.append(")");
	   
	   return sbuf.toString();
   }
   
   public synchronized String quote_list(List<?> values) throws SQLException
   {
	   StringBuffer sbuf = new StringBuffer();
	   sbuf.append("(");
	   for (Object value : values) {
		   if (sbuf.length() > 1) {
			   sbuf.append(",");
		   }
		   sbuf.append(quote_value(value));
	   }
	   sbuf.append(")");
	   
	   return sbuf.toString();
   }   
   
   public synchronized String quote_string(String in)
   {
	   if (in != null) {
		   
		   StringBuffer sbuf = new StringBuffer();
		   sbuf.append("'");
		   for (int i = 0; i < in.length(); i++) {
			   Character c = in.charAt(i);
			   if (escapeSequences.containsKey(c)) {
				   sbuf.append(escapeSequences.get(c));
			   } else {
				   sbuf.append(c);
			   }
		   }
		   sbuf.append("'");
	   
		   return sbuf.toString();
		   
	   } else {
		   
		   return "null";
		   
	   }
   }
   
   public synchronized String conc_attr_and_vals(String table, String[] attr_and_vals) throws SQLException
   {
	   String str = "";
	   String val;
	   
	   for(int i = 0; i < attr_and_vals.length;i++)
	   {
		   if(str != "")
			   str = str + ", ";
		   
		   // check if this field is a foreign_key
		   // if so, no single quotes are needed
		   // the same applies to numeric fields
		   // and also if null values are inserted
		   if(is_foreign_key(table, attr_and_vals[i]) 
				   || is_numeric_field(table, attr_and_vals[i])
				   || attr_and_vals[i+1] == null)
		   {
			   if(is_foreign_key(table, attr_and_vals[i]) && attr_and_vals[i+1].equals("0"))
				   attr_and_vals[i+1] = null; // sometimes null is delivered is 0 ...
			   
			   if(attr_and_vals[i+1] == null)
				   val = "null";
			   else
				   val = attr_and_vals[i+1];
		   }
		   else
		   {
			   val = quote_string(attr_and_vals[i+1]);
		   }
		   
		   str = str + attr_and_vals[i++] + " = " + val;
	   }
	   
	   return str;
   }
   
   public synchronized String conc_tables(String[] tables)
   {
	   String res = tables[0];
	   
	   for(int i=1; i < tables.length; i++)
		   res = res.concat(", "+tables[i]);
	   return res;
   }
   
   
   public synchronized String conc_tables(ArrayList<String> tables)
   {
	   String res = tables.get(0);
	   
	   for(int i=1; i < tables.size(); i++)
		   res = res.concat(", "+tables.get(i));
	   return res;
   }   
   
   public synchronized String conc_cols(ArrayList<String> cols)
   {
	   return conc_cols(cols.toArray(new String[cols.size()]));
   }
   
   public synchronized String conc_cols(String cols[])
   {
	   String res;
	   if(cols.length == 0)
		   res = "";
	   else
		   res = cols[0];	   
	   
	   for(int i=1; i < cols.length; i++)
		   res = res.concat(", "+cols[i]);
	   return res;	  
   }   
   
   
   public synchronized String get_field_value(String fieldname, String tablename, int table_id)
   {
		String value = "";
		String conditions;
		try {
			conditions = this.get_primary_key_name(tablename) + "=" + Integer.toString(table_id);
			
			
			String query = assemble_sql_select_query(fieldname, tablename, conditions);
			
			ResultSet rs = stmt.executeQuery(query);
			
			while (rs.next()) {
				value = rs.getString(1);	
			}	
			
			rs.close();		
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return value;	   
	   
   }
   
   //
   public synchronized String get_non_pk_fieldnames(String table)
   {
	   String fieldnames = "";
	   
	   String query = "SELECT column_name FROM information_schema.`COLUMNS` C " +
	   		"where table_schema = (SELECT schema()) and table_name = '" + table + "' and column_key != 'PRI'";
	   
		try {
			ResultSet rs;			
			rs = stmt.executeQuery(query);
			
			while (rs.next()) {
				fieldnames = StringServices.ConcatString(fieldnames, rs.getString(1), ", ");
			}			
			
			rs.close();
		} catch (SQLException e) {e.printStackTrace();}	
	   
	   return fieldnames;
   }
   
   
   // returns the combination of keys when joining all tables
   
   public synchronized String get_key_joins(String[] tables)
   {
	   return get_key_joins(tables, "''");
   }
   
   
   public synchronized String get_key_joins(String[] tables, String excl_col_names)
   {
	   String[] sublist = new String[tables.length - 1];
	   String conds = "";
	   
	   if(tables.length <= 1)
		   return "";
	   
	   // get sublist (without leading tablename)
	   System.arraycopy(tables, 1, sublist, 0, tables.length - 1);
	   
	   // get key combinations of current table with all in sublist
	   for(int i=0; i < sublist.length; i++)
	   {
		   conds = conc_cond(conds, get_foreign_and_primary_keys_as_cond(tables[0], sublist[i], excl_col_names));		   
	   }
	   
	   // recursive call with sublist
	   conds = conc_cond(conds, get_key_joins(sublist));
	   	   
	  return conds; 
   }
   
   synchronized String get_foreign_and_primary_keys_as_cond(String referencing_table_name, String referenced_table_name, String excl_col_names)
   {
	   String[] cols = get_foreign_and_primary_keys(referencing_table_name, referenced_table_name, excl_col_names);	   
	   
	   // in case there are no foreign key relations we return a empty string
	   if(cols[0] == "")
	   {
		   return "";
	   }
	   else
	   {
		   return (cols[0] + " = " + cols[1]);
	   }
   }

   
   // returns a list containing the referencing column_name (column of the ref_table)
   // and the column_name of the 'referenced table'
   synchronized String[] get_foreign_and_primary_keys(String referencing_table_name, String referenced_table_name)
   {
	   return get_foreign_and_primary_keys(referencing_table_name, referenced_table_name, "''");
   }
   
   
   synchronized String[] get_foreign_and_primary_keys(String referencing_table_name, String referenced_table_name, String excl_col_names)
   {
	   String[] keys = new String[2];
	   String col1 = "";
	   String col2 = "";
	   String t1 = "";
	   String t2 = "";
	   
	   String attr = "REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME, TABLE_NAME, COLUMN_NAME";
	   String table = "information_schema.KEY_COLUMN_USAGE";
	   String conds = new String("(TABLE_NAME = '" + referencing_table_name + 
	   		"' and REFERENCED_TABLE_NAME = '" + referenced_table_name + 
	   		"' or TABLE_NAME = '" + referenced_table_name + 
	   		"' and REFERENCED_TABLE_NAME = '" + referencing_table_name +"') " +
	   		"and constraint_schema = (SELECT schema()) and COLUMN_NAME not in (" + quote_string(excl_col_names) + ")");

	   String query = assemble_sql_select_query(attr, table, conds);
	   
		try {
			ResultSet rs;			
			rs = stmt.executeQuery(query);
			
			while (rs.next()) {
				t1 = rs.getString(1);
				t2 = rs.getString(3);
				col1 = t1 + "." + rs.getString(2);
				col2 = t2 + "." + rs.getString(4);
			}			
			
			rs.close();
		} catch (SQLException e) {e.printStackTrace();}	
		
		// now check which is the referencing table
		if(t1.equals(referencing_table_name))
		{
			// first table name is the referencing table
			keys[0] = col1;
			keys[1] = col2;
		}
		else
		{
			keys[0] = col2;
			keys[1] = col1;			
		}
	   
	   
	   return keys;
   }
   
   public synchronized String get_primary_key_name(String table) throws SQLException
   {
	   String primary_key_name = "";
	   
	   // check for view name
	   if((table.length()- 5 >= 0) && (table.substring(table.length()- 5).compareTo("_view") == 0))
	   {
		   table = table.substring(0,table.length()- 5);
	   }
	   
	   String query = "SELECT K.COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE K " + 
	   	"where table_name = '" + table + "' and constraint_name = 'PRIMARY'  and " +
	   	"constraint_schema = (SELECT schema())";
	   
		ResultSet rs;			
		rs = stmt.executeQuery(query);
		
		while (rs.next()) {
			primary_key_name = rs.getString(1);
		}					
	   
		return primary_key_name;
	   
   }
   
   // prefixes the fieldname with the tablename
   synchronized String get_prefixed_primary_key_name(String table) throws SQLException
   {
	   return prefix(table, get_primary_key_name(table));
   }
   
   public synchronized String prefix(String table, String in)
   {
	   return table + "." + in;
   }
   
   public synchronized String[] prefix(String table, String[] in)
   {
	   for(int i=0; i < in.length; i++)
	   {
		   in[i] = table + "." + in[i];
	   }	   
	   return in;
   }
   
   public synchronized ArrayList<String> prefix(String table, ArrayList<String> in)
   {
	   for(int i=0; i < in.size(); i++)
	   {
		   in.set(i, table + "." + in.get(i));
	   }	   
	   return in;
   }   
   
   
   public synchronized boolean is_foreign_key(String table, String field) throws SQLException
   {
	   
	   boolean is_foreign_key = false;
	   int cnt = 0;
	   
	   String query = "SELECT count(*) FROM information_schema.key_column_usage K " + 	   
	   "where table_name = '" + table + "' and column_name = '" + field + 
	   "' and column_name = REFERENCED_COLUMN_NAME  and " +
	   "constraint_schema = (SELECT schema())";
	   
		ResultSet rs;			
		rs = stmt.executeQuery(query);
		
		while (rs.next()) {
			cnt = rs.getInt(1);
		}					
	   
	   if(cnt == 1)
		   is_foreign_key = true;
	   
	   return is_foreign_key;
   }
   
   synchronized boolean is_numeric_field(String table, String field) throws SQLException
   {
	   boolean is_numeric = false;
	   
	   int cnt = 0;
	   
	   String query = "SELECT count(*) FROM information_schema.columns " + 	   
	   "where table_name = '" + table + "' and column_name = '" + field + 
	   "' and DATA_TYPE in ('int', 'float')";
	   
		ResultSet rs;			
		rs = stmt.executeQuery(query);
		
		while (rs.next()) {
			cnt = rs.getInt(1);
		}					
	   
	   if(cnt == 1)
		   is_numeric = true;
   
	   
	   
	   return is_numeric;
   }
   
   public synchronized String assemble_sql_select_query(String attributes, String tables, String conditions)
   {
	   return assemble_sql_query("SELECT", attributes, tables, conditions, "");	   
   }
   
   public synchronized String assemble_sql_select_distinct_query(String attributes, String tables, String conditions)
   {
	   return assemble_sql_query("SELECT DISTINCT", attributes, tables, conditions, "");
   }
   
   public synchronized String assemble_sql_select_query(String attributes, String tables, String conditions, String options)
   {
	   return assemble_sql_query("SELECT", attributes, tables, conditions, options);
   }
   
   private synchronized String assemble_sql_query(String select, String attributes, String tables, String conditions, String options)
   {
	   StringBuffer query = new StringBuffer();
	   query.append(select);
	   query.append(" ");
	   query.append(attributes);
	   query.append(" FROM ");
	   query.append(tables);
	   if (!conditions.equals(""))
	   {
		   query.append(" WHERE ");
		   query.append(conditions);
	   }
	   query.append(options);

			
	   return query.toString();
	   
   }
   
   public synchronized String assemble_sql_update_query(String attributes_and_values, String table, String conditions)
   {
	   return assemble_sql_update_query(attributes_and_values, table, conditions, "");	   
   }
   
   synchronized String assemble_sql_update_query(String attributes_and_values, String table, String conditions, String options)
   {
	   String query = new String ("UPDATE " + table + " SET " + attributes_and_values);
	   if (!conditions.equals(""))
	   {
		   query = query.concat(" WHERE ");
		   query = query.concat(conditions);
	   }
	   
	   query = query.concat(options);

			
	   return query;
	   
   }
   
   
   // if id is null: return {is, null}
   // if id is not null returns {id_value, =}
   public synchronized id_and_op_struct is_null_key_get_val_and_op(Integer id)
   {
	   return new id_and_op_struct(id);
   }
   
	
   /* return a Java date object
    * Writing this function was stupidly time consuming:
    * Version 1 was getting two Date parameter consisting of
    * the date and the time read from the database (via rs.getDate 
    * and rs.getTime)
    * This somehow always produced wrong dates! Possibly there is
    * some timezone conversion going on in the JDBC driver.
    * 
    * Easiest solution is to parse the date string a construct a fresh
    * java date.
    */

	public synchronized java.util.Date get_java_date(String datetime) throws SQLException
	{
		
		TimeZone tz = TimeZone.getTimeZone("UTC");
		Calendar cal = Calendar.getInstance(tz);
		
		if(datetime != null && !datetime.equals(""))
		{
	
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			formatter.setTimeZone(tz);
			
			try {
				java.util.Date dt = formatter.parse(datetime);
				
				cal.setTime(dt);
				

			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		
		return cal.getTime();
		}
		else
		{
			return null;
		}		
	}
	
	public synchronized java.util.Date get_java_date_time(String datetime) throws SQLException
	{
		
		TimeZone tz = TimeZone.getTimeZone("UTC");
		Calendar cal = Calendar.getInstance(tz);
		
		if(datetime != null && !datetime.equals(""))
		{
	
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			formatter.setTimeZone(tz);
			
			try {
				java.util.Date dt = formatter.parse(datetime);
				
				cal.setTime(dt);
				

			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		
		return cal.getTime();
		}
		else
		{
			return null;
		}		
	}
	
	
	public synchronized java.util.Date get_java_date_time(Timestamp datetime) throws SQLException
	{
		
		TimeZone tz = TimeZone.getTimeZone("UTC");
		Calendar cal = Calendar.getInstance(tz);
		
		cal.setTimeInMillis(datetime.getTime());
						
		return cal.getTime();
	
	}	
	
	
	public String DateAsString(java.util.Date date)
	{
		
		TimeZone tz = TimeZone.getTimeZone("UTC");
		Calendar cal = Calendar.getInstance(tz);
		

		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		formatter.setTimeZone(tz);
		
		cal.setTime(date);
		
		String out=formatter.format(cal.getTime());				
		
		
		return out;
	}
	
	public String JodaDateAsString(DateTime date)
	{
		
		DateTimeFormatter fmt = DateTimeFormat.forPattern(MetaDate.DEFAULT_DATE_FORMAT);
		String date_str = fmt.print(date);
		
		return date_str;
	}	
	
	
	public String build_order_by_string(String order_by)
	{
		String order_by_str = "";
		
		if(order_by != null && !order_by.equals(""))
		{
			order_by_str = " order by " + order_by;
		}		
		
		return order_by_str;
	}
	
	
	public boolean table_exists(String table) throws SQLException {
		
		ResultSet tables = dbm.getTables(null, null, table, null);
		
		return tables.next();
		
	}
	

}


