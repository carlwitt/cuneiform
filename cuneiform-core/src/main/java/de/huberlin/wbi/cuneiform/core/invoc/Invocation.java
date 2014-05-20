/*******************************************************************************
 * In the Hi-WAY project we propose a novel approach of executing scientific
 * workflows processing Big Data, as found in NGS applications, on distributed
 * computational infrastructures. The Hi-WAY software stack comprises the func-
 * tional workflow language Cuneiform as well as the Hi-WAY ApplicationMaster
 * for Apache Hadoop 2.x (YARN).
 *
 * List of Contributors:
 *
 * Jörgen Brandt (HU Berlin)
 * Marc Bux (HU Berlin)
 * Ulf Leser (HU Berlin)
 *
 * Jörgen Brandt is funded by the European Commission through the BiobankCloud
 * project. Marc Bux is funded by the Deutsche Forschungsgemeinschaft through
 * research training group SOAMED (GRK 1651).
 *
 * Copyright 2014 Humboldt-Universität zu Berlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package de.huberlin.wbi.cuneiform.core.invoc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.huberlin.wbi.cuneiform.core.semanticmodel.CompoundExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.DataType;
import de.huberlin.wbi.cuneiform.core.semanticmodel.ForeignLambdaExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NameExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NotBoundException;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NotDerivableException;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Param;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Prototype;
import de.huberlin.wbi.cuneiform.core.semanticmodel.ReduceVar;
import de.huberlin.wbi.cuneiform.core.semanticmodel.SemanticModelVisitor;
import de.huberlin.wbi.cuneiform.core.semanticmodel.StringExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Ticket;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Type;

public abstract class Invocation {
	
	protected static final String FUN_LOG = "cflogmsg";
	protected static final String FUN_LOGFILE = "cflogfilemsg";
	protected static final String FUN_NORMALIZE = "cfnormalize";
	public static final String REPORT_FILENAME = "__report__.txt";
	public static final String SCRIPT_FILENAME = "__script__";
	public static final String SUCCESS_FILENAME = "__success__";
	public static final String STDOUT_FILENAME = "__stdout__.txt";
	public static final String STDERR_FILENAME = "__stderr__.txt";
	public static final String LOCK_FILENAME = "__lock__";


	private Ticket ticket;
	
	public Invocation( Ticket ticket ) {
		setTicket( ticket );
	}
	
	public void evalReport( Set<JsonReportEntry> report ) throws JSONException {
		
		if( report == null )
			throw new NullPointerException( "Report entry set must not be null." );
		
		for( JsonReportEntry entry : report )
			evalReport( entry );
	}
	
	public void evalReport( JsonReportEntry entry ) throws JSONException {
		
		JSONObject obj;
		JSONArray array;
		CompoundExpr ce;
		int i, n;
		
		if( entry == null )
			throw new NullPointerException( "Report entry must not be null." );
		
		
		if( !entry.getKey().equals( JsonReportEntry.KEY_INVOC_OUTPUT ) )
			return;
		
		
		obj = entry.getValueJsonObj();
		
		
		for( NameExpr nameExpr : ticket.getOutputList() ) {
			
			array = obj.getJSONArray( nameExpr.getId() );
			ce = new CompoundExpr();
			n = array.length();
			
			for( i = 0; i < n; i++ )
				ce.addSingleExpr( new StringExpr( array.getString( i ) ) );
			
			ticket.setValue( nameExpr, ce );
		}
			
	}
	
	public String getFunDef() throws NotDerivableException {
		return defFunctionLog()
			// +defFunctionLogUsr()
			+defFunctionNormalize()
			+defFunctionLogFile();
	}
	
	public String getLangLabel() {
		return ticket.getLangLabel();
	}
	
	public Set<String> getStageInList() throws NotDerivableException {
		
		CompoundExpr ce;
		Set<String> set;

		try {

			ce = new CompoundExpr();
			set = new HashSet<>();
			
			for( NameExpr nameExpr : ticket.getNameSet() )
				
				if( isParamStage( nameExpr.getId() ) ) {
					
					ce= ticket.getExpr( nameExpr );
					for( String s : ce.normalize() )						
						set.add( s );
				}
			
			return set;
		}
		catch( NotBoundException e ) {
			throw new RuntimeException( e.getMessage() );
		}
	}
	
	public Set<String> getStageOutList() throws NotDerivableException {
		
		CompoundExpr ce;
		Set<String> set;

		try {

			ce = new CompoundExpr();
			set = new HashSet<>();
			
			for( NameExpr nameExpr : ticket.getOutputList() )
				
				if( isOutputStage( nameExpr.getId() ) ) {
					
					ce= ticket.getOutputValue( nameExpr );
					for( String s : ce.normalize() )						
						set.add( s );
				}
			
			return set;
		}
		catch( NotBoundException e ) {
			throw new RuntimeException( e.getMessage() );
		}
	}
	
	
	
	public UUID getRunId() {
		return ticket.getRunId();
	}
	
	public long getTaskId() {
		return ticket.getLambdaId();
	}
	
	public String getTaskName() {
		return null;
	}
	
	public Ticket getTicket() {
		return ticket;
	}
	
	public long getTicketId() {
		return ticket.getTicketId();
	}
	
	public boolean hasTaskName() {
		return false;
	}
	
	public void setTicket( Ticket ticket ) {
		
		if( ticket == null )
			throw new NullPointerException( "Ticket must not be null." );
		
		this.ticket = ticket;
	}
	
	public String toScript() throws NotBoundException, NotDerivableException {
		
		StringBuffer buf;
		
		buf = new StringBuffer();
		
		// insert shebang
		buf.append( getShebang() ).append( '\n' );
		
		// import libraries
		buf.append( comment( "import libraries" ) );
		buf.append( getImport() ).append( '\n' );
		
		// define necessary functions
		buf.append( comment( "define necessary functions" ) );
		buf.append( getFunDef() ).append( '\n' );

		// bind single output variables to default values
		buf.append( comment( "bind single output variables to default values" ) );
		for( String outputName : getSingleOutputNameSet() )
			buf.append(
				varDef(
					outputName,	
					quote( outputName ) ) );
		buf.append( '\n' );
		
		// bind input parameters
		buf.append( comment( "bind input parameters" ) );
		for( String paramName : getSingleParamNameSet() ) {
			
			if( paramName.equals( SemanticModelVisitor.LABEL_TASK ) )
				continue;
			
			buf.append( varDef( paramName, quote( getResolveableBoundToSingleParam( paramName ) ) ) );
		}
		for( String paramName : getReduceParamNameSet() )
			buf.append( varDef( paramName, getReduceParam( paramName ) ) );		
		buf.append( '\n' );
		
		// report stage in file sizes and report error when something is missing
		buf.append( comment( "report stage in file sizes and report error when something is missing" ) );
		buf.append( getStageInCollect() ).append( '\n' );
				
		// insert function body
		buf.append( comment( "insert function body" ) );
		buf.append( ticket.getBody() ).append( '\n' );
		
		// check post
		buf.append( comment( "check post" ) );
		buf.append( getCheckPost() ).append( '\n' );
		
		// rename output files
		buf.append( comment( "rename output files" ) );
		buf.append( getOutputRename() ).append( '\n' );
		
		// collect output variables
		buf.append( comment( "collect output variables" ) );
		buf.append( getOutputCollect() ).append( '\n' );
		
		// collect stage out information
		buf.append( comment( "collect stage out information" ) );
		buf.append( getStageOutCollect() ).append( '\n' );
		
		return buf.toString();
	}

	protected abstract String callFunction( String name, String... argValue );
	protected abstract String clip( String varName );
	protected abstract String comment( String comment );
	protected abstract String copyArray( String from, String to );
	protected abstract String defFunctionLog() throws NotDerivableException;
	protected abstract String defFunctionLogFile() throws NotDerivableException;
	protected abstract String defFunctionNormalize() throws NotDerivableException;
	protected abstract String dereference( String varName );
	protected abstract String fileSize( String filename );
	protected abstract String forEach( String listName, String elementName, String body );
	protected abstract String getShebang();
	protected abstract String getCheckPost();
	protected abstract String getImport();
	protected abstract String ifListIsNotEmpty( String listName, String body );
	
	protected abstract String ifNotFileExists( String fileName, String body );
	protected abstract String join( String ... elementList );
	protected abstract String listAppend( String listName, String element );
	protected abstract String listToBraceCommaSeparatedString( String listName, String stringName, String open, String close );
	protected abstract String newList( String listName );
	protected abstract String quote( String content );
	protected abstract String raise( String msg );
	protected abstract String symlink( String src, String dest );
	protected abstract String varDef( String varname, String value );
	protected abstract String varDef( String varname, CompoundExpr ce ) throws NotDerivableException;

	private boolean isOutputStage( String outputName ) {

		Prototype prototype;
		Type type;
		DataType dataType;
		
		prototype = ticket.getPrototype();
		
		for( NameExpr nameExpr : prototype.getOutputList() )
			
			if( nameExpr.getId().equals( outputName ) ) {
				
				if( !nameExpr.hasType() )
					return false;
								
				type = nameExpr.getType();
				if( !( type instanceof DataType ) )
					return false;
				
				dataType = ( DataType )type;
				
				return dataType.getId().equals( SemanticModelVisitor.LABEL_FILE );
			}
		
		throw new RuntimeException( "Output not found." );
	}
	
	private boolean isParamStage( String paramName ) {

		Prototype prototype;
		Type type;
		DataType dataType;
		
		prototype = ticket.getPrototype();
		
		for( NameExpr nameExpr : prototype.getParamNameSet() )
			
			if( nameExpr.getId().equals( paramName ) ) {
				
				if( !nameExpr.hasType() )
					return false;
								
				type = nameExpr.getType();
				if( !( type instanceof DataType ) )
					return false;
				
				dataType = ( DataType )type;
				
				return dataType.getId().equals( SemanticModelVisitor.LABEL_FILE );
			}
		
		throw new RuntimeException( "Output not found." );
	}
	
	private int getOutputChannel( String outputName ) {
		
		Prototype prototype;
		int i, n;
		NameExpr output;
		
		prototype = ticket.getPrototype();
		n = prototype.getNumOutput();
		
		
		for( i = 0; i < n; i++ ) {
			
			output = prototype.getOutput( i );
			
			if( output.getId().equals( outputName ) )
				return i+1;
		}
		
		throw new RuntimeException( "Output not found." );
	}
	
	private String getOutputCollect() {
		
		StringBuffer buf;
		
		buf = new StringBuffer();
		
		buf.append( varDef( "CFSTR", quote( "" ) ) );
		for( String outputName : getSingleOutputNameSet() )
			
			buf.append(
				varDef(
					"CFSTR",
					join(
						dereference( "CFSTR" ),
						quote( "," ),
						quote( outputName+":[\"" ),
						dereference( outputName ),
						quote( "\"]" ) ) ) );
			
		
		
		for( String outputName : getReduceOutputNameSet() ) {
			
			buf
				.append( varDef( "CFSTR1", "" ) )
				.append( 
					forEach(
						outputName,
						"CFI",
						varDef(
							"CFSTR1",
							join(
								dereference( "CFSTR1" ),
								quote( ",\"" ),
								dereference( "CFI" ),
								quote( "\"" )	) ) ) )
				.append( clip( "CFSTR1" ) )
				.append(
					varDef(
						"CFSTR",
						join(
							dereference( "CFSTR" ),
							quote( "," ),
							quote( outputName+":[" ),
							dereference( "CFSTR1" ),
							quote( "]" ) ) ) );
		}
		
		buf
			.append( clip( "CFSTR" ) )
			
			.append(
				varDef(
					"CFSTR",
					join(
						quote( "{" ),
						dereference( "CFSTR" ),
						quote( "}" ) ) ) )
						
			.append(
				callFunction(
					FUN_LOG,
					quote( JsonReportEntry.KEY_INVOC_OUTPUT ),
					dereference( "CFSTR" ) ) ).append( '\n' );
		
		
		return buf.toString();
		
	}
	
	private String getOutputRename() {
		
		StringBuffer buf;
		
		buf = new StringBuffer();

		for( String outputName : getSingleOutputNameSet() )
			
			if( isOutputStage( outputName ) ) {
				
				buf.append(
					varDef(
						"CFFILENAME",
						callFunction(
							FUN_NORMALIZE,
							String.valueOf( getOutputChannel( outputName ) ),
							dereference( outputName ) ) ) );
				
				buf.append(
					symlink(
						dereference( outputName ),
						dereference( "CFFILENAME" ) ) );
							
				buf.append(
					varDef(
						outputName,
						dereference( "CFFILENAME" ) ) );
			}
		
		for( String outputName : getReduceOutputNameSet() )

			if( isOutputStage( outputName ) ) {
				
				buf.append( newList( "CFLIST" ) );
				
				buf.append(
					forEach(
						outputName,
						"CFFILENAME",
						
						varDef(
							"CFNEWFILENAME",
							callFunction(
								FUN_NORMALIZE,
								String.valueOf(
									getOutputChannel( outputName ) ),
								dereference( "CFFILENAME" ) ) )
								
						+listAppend( "CFLIST", dereference( "CFNEWFILENAME" ) )
						
						+symlink(
							dereference( "CFFILENAME" ),
							dereference( "CFNEWFILENAME" ) ) ) );

				// buf.append( varDef( outputName, dereference( "__LIST" ) ) );
				buf.append( copyArray( "CFLIST", outputName ) );
			}
		
		return buf.toString();
	}
	
	private Set<String> getReduceOutputNameSet() {
		
		List<NameExpr> outputList;
		Set<String> reduceList;
		
		outputList = ticket.getOutputList();
		reduceList = new HashSet<>();
		
		for( NameExpr nameExpr : outputList )
			
			if( nameExpr instanceof ReduceVar )
				reduceList.add( nameExpr.getId() );
		
		return reduceList;
	}
	
	private Set<String> getReduceParamNameSet() {

		Set<Param> paramSet;
		Set<String> reduceList;
		
		paramSet = ticket.getParamSet();
		reduceList = new HashSet<>();
		
		for( Param param : paramSet )
			
			if( param instanceof ReduceVar )
				reduceList.add( ( ( ReduceVar )param ).getId() );
		
		
		return reduceList;
	}
	
	private CompoundExpr getReduceParam( String paramName ) throws NotBoundException {
		return ticket.getExpr( paramName );
	}
	


	private String getResolveableBoundToSingleParam( String paramName )
	throws NotBoundException, NotDerivableException {		
		return ticket.getExpr( paramName ).normalize().get( 0 );
	}
	
	private Set<String> getSingleOutputNameSet() {
		
		List<NameExpr> outputList;
		Set<String> nonReduceList;
		
		outputList = ticket.getOutputList();
		nonReduceList = new HashSet<>();
		
		for( NameExpr nameExpr : outputList )
			
			if( !( nameExpr instanceof ReduceVar ) )
				nonReduceList.add( nameExpr.getId() );
		
		return nonReduceList;
	}
	
	private Set<String> getSingleParamNameSet() {

		Set<Param> paramSet;
		Set<String> nonReduceList;
		
		paramSet = ticket.getParamSet();
		nonReduceList = new HashSet<>();
		
		for( Param param : paramSet )
			
			if( !( param instanceof ReduceVar ) )
				for( NameExpr nameExpr : param.getNameExprSet() )
					nonReduceList.add( nameExpr.getId() );
		
		return nonReduceList;
		
	}
	
	private String getStageInCollect() {
		
		StringBuffer buf;
		
		buf = new StringBuffer();
		
		
		for( String inputName : getSingleParamNameSet() )
			if( isParamStage( inputName ) )
				buf
					.append( ifNotFileExists(
					dereference( inputName ),
					raise( join( quote( "Stage in: A file " ), dereference( inputName ),
						quote( " should be present but has not been found." ) ) ) ) )
				
					.append( varDef( "SIZE", fileSize( dereference( inputName ) ) ) )
						
					.append(
						callFunction(
							FUN_LOGFILE,
							dereference( inputName ),
							JsonReportEntry.KEY_FILE_SIZE_STAGEIN,
							dereference( "SIZE" ) ) ).append( '\n' );
					
		
		for( String inputName : getReduceParamNameSet() )
			if( isParamStage( inputName ) )
				
				buf.append(
					forEach(
						inputName,
						"CFI",
						ifNotFileExists(
							dereference( "CFI" ),
							raise(
								join(
									quote( "Stage in: A file " ),
									dereference( "CFI" ),
									quote( " should be present but has not been found." ) ) ) )
						
									
						+varDef( "SIZE", fileSize( dereference( "CFI" ) ) )
						
						+callFunction(
							FUN_LOGFILE,
							dereference( "CFI" ),
							JsonReportEntry.KEY_FILE_SIZE_STAGEIN,
							dereference( "SIZE" ) ) ) );				
		
		return buf.toString();
	}
	
	private String getStageOutCollect() {
		
		StringBuffer buf;
		
		buf = new StringBuffer();
		
		
		for( String outputName : getSingleOutputNameSet() )
			if( isOutputStage( outputName ) )
				buf
					.append( ifNotFileExists(
					dereference( outputName ),
					raise( join( quote( "Stage out: A file " ), dereference( outputName ),
						quote( " should have been created but has not been found." ) ) ) ) )
				
					.append( varDef( "SIZE", fileSize( dereference( outputName ) ) ) )
						
					.append(
						callFunction(
							FUN_LOGFILE,
							dereference( outputName ),
							JsonReportEntry.KEY_FILE_SIZE_STAGEOUT,
							dereference( "SIZE" ) ) ).append( '\n' );
					
		
		for( String outputName : getReduceOutputNameSet() )
			if( isOutputStage( outputName ) )
				
				buf.append(
					forEach(
						outputName,
						"CFI",
						ifNotFileExists(
							dereference( "CFI" ),
							raise(
								join(
									quote( "Stage out: A file " ),
									dereference( "CFI" ),
									quote( " should be present but has not been found." ) ) ) )
						
									
						+varDef( "SIZE", fileSize( dereference( "CFI" ) ) )
						
						+callFunction(
							FUN_LOGFILE,
							dereference( "CFI" ),
							JsonReportEntry.KEY_FILE_SIZE_STAGEOUT,
							dereference( "SIZE" ) ) ) );				
		
		return buf.toString();
	}
	
	public static Invocation createInvocation( Ticket ticket ) {
		
		switch( ticket.getLangLabel() ) {
		
			case ForeignLambdaExpr.LANGID_BASH : return new BashInvocation( ticket );
			case ForeignLambdaExpr.LANGID_R : return new RInvocation( ticket );
			default : throw new RuntimeException( "Language label not recognized." );
		}
	}
}