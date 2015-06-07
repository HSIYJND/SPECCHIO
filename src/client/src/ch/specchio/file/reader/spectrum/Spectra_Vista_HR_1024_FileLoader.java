package ch.specchio.file.reader.spectrum;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;


import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import ch.specchio.spaces.MeasurementUnit;
import ch.specchio.types.MetaParameter;
import ch.specchio.types.MetaParameterFormatException;
import ch.specchio.types.Metadata;
import ch.specchio.types.SpectralFile;
import ch.specchio.types.spatial_pos;

public class Spectra_Vista_HR_1024_FileLoader extends SpectralFileLoader {
	
	SpectralFile spec_file;
	Metadata md_tgt, md_ref;

	public Spectra_Vista_HR_1024_FileLoader()  {
		super("SVC HR 1024");
	}

	public SpectralFile load(File file) throws IOException, MetaParameterFormatException
	{
		spec_file = new SpectralFile();
		spec_file.setNumberOfSpectra(3); // always a target and a reference radiance plus a reflectance
		
		spec_file.setPath(file.getAbsolutePath());		
		spec_file.setFilename(file.getName());
		spec_file.setFileFormatName(this.file_format_name);
		
		// spectrum number is contained in the filename, but only in the standard case, if files are named manually, then this does not apply
//		spec_file.spectra_numbers[0] = Integer.valueOf(spec_file.base_name.substring(spec_file.base_name.length()-4));
//		spec_file.spectra_numbers[1] = spec_file.spectra_numbers[0];
//		spec_file.spectra_numbers[2] = spec_file.spectra_numbers[0];
						
		spec_file.addSpectrumFilename(spec_file.getFilename()); // target name
		spec_file.addSpectrumFilename(spec_file.getFilename()); // reference name
		spec_file.addSpectrumFilename(spec_file.getFilename()); // reflectance name

		// default settings
		spec_file.addMeasurementUnits(0, MeasurementUnit.DN); // DN
		spec_file.addMeasurementUnits(1, MeasurementUnit.DN); // DN
		spec_file.addMeasurementUnits(2, MeasurementUnit.Reflectance); // Reflectance
		
//		spec_file.capture_dates = new Date[spec_file.no_of_spectra()]; 
		
		md_tgt = new Metadata();
		md_ref = new Metadata();
		
		file_input = new FileInputStream (file);			
				
		data_in = new DataInputStream(file_input);
		
		read_HR1024_file(data_in, spec_file);
		
		spec_file.addEavMetadata(md_tgt);
		spec_file.addEavMetadata(md_ref);

		
		data_in.close ();
		
		return spec_file;
	}
	
	public void read_HR1024_file(DataInputStream in, SpectralFile f) throws IOException, MetaParameterFormatException
	{
		String line;
		boolean hdr_ended = false;
		
		// use buffered stream to read lines
		BufferedReader d = new BufferedReader(new InputStreamReader(in));
		
		// read line by line
		while(!hdr_ended && (line=d.readLine()) != null)
		{
			// tokenise the line
			String[] tokens = line.split("=");
			
			// analyse the tokens
			hdr_ended = analyse_HR1024_file(tokens, d, f);						
		}		
		
		// read the measurements
		f.setMeasurements(read_data(data_in, d));
		
		f.addNumberOfChannels(f.getMeasurement(0).length);
		
		

		
		
	}
	
	public boolean analyse_HR1024_file(String[] tokens, BufferedReader in, SpectralFile hdr) throws MetaParameterFormatException
	{
		
			

		String t1 = tokens[0];
		boolean hdr_ended = false;
		
		try {
			
		if(t1.equals("/*** Spectra Vista HR-1024 ***/"))
		{
			hdr.setCompany("SVC");
		}
		
		if(t1.equals("/*** Spectra Vista SIG Data ***/"))
		{
			hdr.setCompany("SVC");
		}		
		
		if(t1.equals("instrument"))
		{
			tokens[1] = tokens[1].replace(" ", ""); // remove spaces from token 2
			String[] instr_data = tokens[1].split(":");			
			hdr.setInstrumentTypeNumber(1024);
			hdr.setInstrumentNumber(instr_data[1]);			
		}
		
		if(t1.equals("units"))
		{
			String[] sub_tokens = tokens[1].split(", ");
			
			for(int i=0;i<2;i++)
			{			
				if(sub_tokens[i].contains("Radiance"))
				{
					hdr.setMeasurementUnits(i, MeasurementUnit.Radiance);
				}
				if(sub_tokens[i].contains("Counts"))
				{
					hdr.setMeasurementUnits(i, MeasurementUnit.DN);
				}
				
			}			
			
		}
		
//		time= 7/18/10 9:47:09 AM, 7/18/10 9:47:31 AM
//		longitude= 11121.2335,W, 11121.2324,W
//		latitude= 5330.5955,N, 5330.5964,N
//		gpstime= 154336.000, 154356.000
		
		if(t1.equals("time"))
		{
			// remove first space
			//tokens[1] = tokens[1].replaceFirst(" ", "");
			
			String[] time_data = tokens[1].split(",");
			
			
			hdr.setCaptureDate(1, get_date_and_time_from_HR_string(time_data[1]));
			hdr.setCaptureDate(2, hdr.getCaptureDate(1)); // reflectance capture date is same as radiance of target
			
			if (time_data[0].equals(" "))
			{
				hdr.setCaptureDate(0,  hdr.getCaptureDate(1));
			}
			else
			{
				hdr.setCaptureDate(0, get_date_and_time_from_HR_string(time_data[0]));
			}
			
				

		}
		
		
		if(t1.equals("comm"))
		{
			hdr.setComment(tokens[1]);
		}
		
		if(t1.equals("optic") && tokens[1].contains(" LENS"))
		{
			// assumption: lenses do not change between reference and target
			
			String[] sub_tokens = tokens[1].split(", ");

			MetaParameter mp = MetaParameter.newInstance(attributes_name_hash.get("Optics Name"));
			mp.setValue(sub_tokens[0].substring(1), "String"); // cut first whitespace
			md_tgt.addEntry(mp); 	
			
			 mp = MetaParameter.newInstance(attributes_name_hash.get("Optics Name"));
			 mp.setValue(sub_tokens[1], "String");
			 md_ref.addEntry(mp); 

			
//			String str = sub_tokens[0].replaceFirst(" LENS", "");
//			
//			str = str.replace(" ", "");
//			
//			hdr.setForeopticDegrees(Integer.valueOf(str));	
						
		}		
		
		if(t1.equals("longitude") && tokens[1].length() > 3)
		{					
			double longitudes[] = read_gps_data(tokens[1]);
			
			if(longitudes != null)
			{				
				add_spatial_positions_if_needed(hdr);
				
				hdr.getPos(0).longitude = longitudes[0]; // reference
				hdr.getPos(1).longitude = longitudes[1]; // tgt radiance
				hdr.getPos(2).longitude = longitudes[1]; // tgt reflectance
			}
			
		}
		
		if(t1.equals("latitude") && tokens[1].length() > 3)
		{					
			double latitudes[] = read_gps_data(tokens[1]);
			
			if(latitudes != null)
			{
				add_spatial_positions_if_needed(hdr);

				hdr.getPos(0).latitude = latitudes[0]; // reference
				hdr.getPos(1).latitude = latitudes[1]; // tgt radiance
				hdr.getPos(2).latitude = latitudes[1]; // tgt reflectance
			}
		}			
		
		if(t1.equals("data"))
		{
			hdr_ended = true;
		}
		
		} catch(java.lang.NumberFormatException e)
		{
			System.out.println(e);
		}
		catch(java.lang.NullPointerException e)
		{
			System.out.println(e);
		}
		
		
		return hdr_ended;
		
	}
	
	
	private void add_spatial_positions_if_needed(SpectralFile hdr)
	{
		if(hdr.getPos().size() == 0)
		{
			hdr.addPos(new spatial_pos());
			hdr.addPos(new spatial_pos());
			hdr.addPos(new spatial_pos());
		}
	}
	
	
	double[] read_gps_data(String str)
	{
		// Older SIG files
		//  11121.2335,W, 11121.2324,W
		//  5330.5955,N, 5330.5964,N
		
		// Newer SIG files
//		longitude= 08536.6045W     , 08536.6034W     
//		latitude= 1055.3959N      , 1055.3963N    		
		
		double[] file_coord = new double[2];	
		double[] coord = new double[2];	
		
		str = str.replace(" ", "");
		
		String[] coords = str.split(",");
		
		if (coords.length == 4)
		{
			
			file_coord[0] = Double.valueOf(coords[0]);
			file_coord[1] = Double.valueOf(coords[2]);
			
			coord[0] = spec_file.DDDmm2DDDdecimals(file_coord[0]);
			coord[1] = spec_file.DDDmm2DDDdecimals(file_coord[1]);
			
			if (coords[0].equals("S") || coords[0].equals("E"))
			{
				coord[0] = coord[0]*(-1);
				coord[1] = coord[1]*(-1);
			}
			
			return(coord);			
			
		}
		else if (coords.length == 2)
		{
			
			file_coord[0] = Double.valueOf(coords[0].substring(0, coords[0].length()-1));
			file_coord[1] = Double.valueOf(coords[1].substring(0, coords[1].length()-1));
			
			coord[0] = spec_file.DDDmm2DDDdecimals(file_coord[0]);
			coord[1] = spec_file.DDDmm2DDDdecimals(file_coord[1]);
			
			String c1_ = coords[0].substring(coords[0].length()-1);
			String c2_ = coords[1].substring(coords[0].length()-1);
			
			if (c1_.equals("S") || c2_.equals("E"))
			{
				coord[0] = coord[0]*(-1);
				coord[1] = coord[1]*(-1);
			}
			
			return(coord);			

		}		
		else
		{
			return null;
		}
	}
	
	
	
	
//	spatial_pos read_gps_data(DataInputStream in) throws IOException
//	{
//		spatial_pos pos = null;
//		
//		// skip true heading and speed
//		skip(in, 16);
//		
//		double lat = read_double(in);
//		double lon = read_double(in);
//		double alt = read_double(in);
//		
//		// reformat to dd.mmmmmmmmm
//		int lat_deg = (int)lat/100;
//		double lat_min = (lat - lat_deg*100)/60;
//		
//		int lon_deg = (int)lon/100;
//		double lon_min = (lon - lon_deg*100)/60;
//		
//		// only create position record if the position is not zero
//		if(lat != 0 && lon != 0 && alt != 0)
//		{
//			pos = new spatial_pos();
//			pos.latitude = lat_deg + lat_min;
//			pos.longitude = lon_deg + lon_min;
//			pos.altitude = alt;			
//		}
//		
//		
//		// skip rest
//		skip(in, 16);
//				
//		return pos;
//	}
		
	
	
	DateTime get_date_and_time_from_HR_string(String str)
	{
		
		str = str.replaceFirst(" ", "");
		String[] date_and_time = str.split(" ");
		

		String[] date = date_and_time[0].split("/");
		String[] time = date_and_time[1].split(":");
		
		int hrs = Integer.valueOf(time[0]);
		// AM and PM check
		if (date_and_time[2].equals("AM"))
		{
			if (hrs == 12) hrs = hrs - 12;			
		}
		if (date_and_time[2].equals("PM"))
		{
			if (hrs >= 1 && hrs < 12) hrs = hrs + 12;			
		}		
		
		int year = Integer.valueOf(date[2]);
		
		if (date[2].length() == 2)
		{
			year = year + 2000;
		}
		
//		TimeZone tz = TimeZone.getTimeZone("UTC");
//		Calendar cal = Calendar.getInstance(tz);
//		cal.set((year), Integer.valueOf(date[0]) - 1, // month is zero based!
//				Integer.valueOf(date[1]), hrs, 
//				Integer.valueOf(time[1]), Integer.valueOf(time[2]));
//
//		
//		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
//		formatter.setTimeZone(tz);
		
		//String out=formatter.format(cal.getTime());


// int hh = cal.get(Calendar.HOUR_OF_DAY);
		
		DateTime dt;

		// no idea if this is American or European date formatting ...
		
		if(Integer.valueOf(date[1]) > 12) // this cannot be a month!
		{
			dt = new DateTime(year, Integer.valueOf(date[0]), Integer.valueOf(date[1]), hrs, Integer.valueOf(time[1]), Integer.valueOf(time[2]), DateTimeZone.UTC); // joda months start at 1
		}
		else
		{
			dt = new DateTime(year, Integer.valueOf(date[1]), Integer.valueOf(date[0]), hrs, Integer.valueOf(time[1]), Integer.valueOf(time[2]), DateTimeZone.UTC); // joda months start at 1
		}
		

		
		return dt;
		
	}
	
	Float[][] read_data(DataInputStream in, BufferedReader d) throws IOException
	{
		Float[][] f;
		String line;
		ArrayList<Float> target, reference, reflectance, wvls;
		
		wvls = new ArrayList<Float>();
		target = new ArrayList<Float>();
		reference = new ArrayList<Float>();
		reflectance = new ArrayList<Float>();
		
		int reference_total = 0;
				
		// read line by line
		while((line=d.readLine()) != null)
		{
			
			// GER specific treatment: needed for the HR????
			// There are some strange cases where radiance values can be negative (of the last channel)
			// the minus signs are inserted into the second space that is separating the values
			// In order to have the values still being separated by two spaces, a replace must be done:
			//line = line.replaceAll("-", " -");
			
			// tokenise the line
			String[] tokens = line.split("\\s+"); // values are separated by one or more spaces
			
			// first token is wavelength
			wvls.add(Float.valueOf(tokens[0]));
			
			// second token is radiance of reference
			Float ref_val = Float.valueOf(tokens[1]);
			reference.add(ref_val);
			reference_total += ref_val;
			
			// third token is radiance of target
			target.add(Float.valueOf(tokens[2]));
			
			// 4th token is radiance of reflectance
			reflectance.add(Float.valueOf(tokens[3]) / 100); // normalise to range 0:1			
			
		}
		
		
		// check if this is a radiance only file (no reference taken)
		// in this case, the reference readings are all set to 1.00
		if (reference.size() == reference_total)
		{ 
			// only radiance of target
			
			
			f = new Float[1][target.size()];			
			f[0] = target.toArray(f[0]);		
			
			// Why to we do this????
			ArrayList<String> local_spectra_names = new ArrayList<String>(spec_file.getSpectraNames());
			ArrayList<String> local_spectra_filenames = spec_file.getSpectraNames();
//			Integer[] local_spectra_numbers = spec_file.spectra_numbers;
			DateTime[] local_capture_dates = spec_file.getCaptureDates();
			ArrayList<Integer> local_measurement_units = new ArrayList<Integer>(spec_file.getMeasurementUnits());
			//spatial_pos[] local_pos = spec_file.pos;	
			
			spec_file.setNumberOfSpectra(1);
			
			spec_file.setSpectraNames(new ArrayList<String>());	
			spec_file.setSpectraFilenames(new ArrayList<String>());	
			spec_file.setMeasurementUnits(new ArrayList<Integer>());	
			
			spec_file.addSpectrumName(local_spectra_names.get(1));
			spec_file.addSpectrumFilename(local_spectra_filenames.get(1));
//			spec_file.spectra_numbers[0] = local_spectra_numbers[1];
			spec_file.setCaptureDate(0, local_capture_dates[1]);
			spec_file.addMeasurementUnits(local_measurement_units.get(1));
			//spec_file.pos[0] = local_pos[1];
			
		}
		else
		{
			
			f = new Float[3][target.size()];
			
			f[0] = reference.toArray(f[0]);
			f[1] = target.toArray(f[1]);
			f[2] = reflectance.toArray(f[2]);
			
		}
		
		for(int i=0;i<f.length;i++)
		{
		
			spec_file.addWvls(new Float[target.size()]);
			spec_file.setWvls(i, wvls.toArray(spec_file.getWvls(0)));
		
		}

		
		

//		li = wvls.listIterator();
//		int i = 0;
//		while(li.hasNext())
//		{
//			spec_file.wvls[i++] = (Float)li.next();
//		}				
//		
//		
//		li = reference.listIterator();
//		i = 0;
//		while(li.hasNext())
//		{
//			f[0][i++] = (Float)li.next();
//		}		
//		
//		
//		i = 0;
//		li = target.listIterator();
//		while(li.hasNext())
//		{
//			f[1][i++] = (Float)li.next();
//		}
//		
//		i = 0;
//		li = reflectance.listIterator();
//		while(li.hasNext())
//		{
//			f[2][i++] = (Float)li.next();
//		}
		
		
		return f;
		
		
		
	}

}
