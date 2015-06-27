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

package de.huberlin.wbi.cuneiform.core.repl;

import java.util.LinkedList;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.huberlin.wbi.cuneiform.core.semanticmodel.ApplyExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.BaseBlock;
import de.huberlin.wbi.cuneiform.core.semanticmodel.BaseNodeVisitor;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Block;
import de.huberlin.wbi.cuneiform.core.semanticmodel.EnumHelper;
import de.huberlin.wbi.cuneiform.core.semanticmodel.CompoundExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.CondExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.CurryExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.ForeignLambdaExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.HasFailedException;
import de.huberlin.wbi.cuneiform.core.semanticmodel.JsonReportEntry;
import de.huberlin.wbi.cuneiform.core.semanticmodel.LambdaExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NameExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NativeLambdaExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NotBoundException;
import de.huberlin.wbi.cuneiform.core.semanticmodel.NotDerivableException;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Prototype;
import de.huberlin.wbi.cuneiform.core.semanticmodel.QualifiedTicket;
import de.huberlin.wbi.cuneiform.core.semanticmodel.SemanticModelException;
import de.huberlin.wbi.cuneiform.core.semanticmodel.SingleExpr;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Ticket;
import de.huberlin.wbi.cuneiform.core.semanticmodel.TopLevelContext;
import de.huberlin.wbi.cuneiform.core.ticketsrc.NodeVisitorTicketSrc;

public class DynamicNodeVisitor extends BaseNodeVisitor {
	
	private final NodeVisitorTicketSrc ticketSrc;
	private final BaseRepl repl;
	private final UUID queryId;
	private BaseBlock currentBlock;
	private final LinkedList<BaseBlock> blockStack;
	private final Log log;
	private final Log statLog;
	
	public DynamicNodeVisitor( NodeVisitorTicketSrc ticketSrc, BaseRepl repl, TopLevelContext tlc ) {
		
		if( ticketSrc == null )
			throw new NullPointerException( "Ticket source must not be null." );
		
		if( repl == null )
			throw new NullPointerException( "REPL must not be null." );
		
		this.repl = repl;
		this.ticketSrc = ticketSrc;

		queryId = UUID.randomUUID();
		blockStack = new LinkedList<>();
		log = LogFactory.getLog( DynamicNodeVisitor.class );
		statLog = LogFactory.getLog( "statLogger" );

		setTopLevelContext( tlc );
	}

	@Override
	public CompoundExpr accept( NameExpr nameExpr ) throws HasFailedException, NotBoundException {		
		return currentBlock.getExpr( nameExpr ).visit( this );
	}

	@Override
	public CompoundExpr accept( CondExpr condExpr ) throws HasFailedException, NotBoundException {
		
		CompoundExpr ifExpr, ifExpr1;
		CondExpr cnd1;
		
		ifExpr = condExpr.getIfExpr();
		if( ifExpr == null )
			throw new NullPointerException( "Condition must not be null." );
		
		// try to reduce task expression
		ifExpr1 = ifExpr.visit( this );
		if( ifExpr1 == null )
			throw new NullPointerException( "Evaluation of condition must not result in null." );
		
		try {
			
			if( ifExpr1.isNormal() ) {
				
				// the result could be determined
				
				if( ifExpr1.getNumAtom() == 0 )
					return condExpr.getElseExpr().visit( this );
				
				return condExpr.getThenExpr().visit( this );
				
			}
			
			// the result is still open
			cnd1 = new CondExpr( ifExpr1, condExpr.getThenExpr(), condExpr.getElseExpr() );
			
			return new CompoundExpr( cnd1 );
		}
		catch( NotDerivableException e ) {
			throw new RuntimeException( e );
		}

	}

	@Override
	public CompoundExpr accept( ApplyExpr applyExpr ) throws HasFailedException, NotBoundException {
		
		ApplyExpr applyExpr1;
		CompoundExpr taskExpr1, paramExpr, paramExpr1;
		boolean rest;
		int channel;
		SingleExpr se;
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor accept ApplyExpr: "+applyExpr.toString().replace( '\n', ' ' ) );
		
		// prepare reduced apply expression
		channel = applyExpr.getChannel();
		rest = applyExpr.hasRest();
		applyExpr1 = new ApplyExpr( channel, rest );

		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor accept ApplyExpr: Trying to reduce task expression." );		
		
		// try to reduce task expression
		taskExpr1 = applyExpr.getTaskExpr().visit( this );
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor accept ApplyExpr: Replacing task expression with "+taskExpr1+"." );
		applyExpr1.setTaskExpr( taskExpr1 );
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor accept ApplyExpr: Trying to reduce parameter list." );		
		
		// try to reduce the parameter list
		try {
			for( NameExpr nameExpr : applyExpr.getNameSet() ) {
				
				paramExpr = applyExpr.getExpr( nameExpr );
				paramExpr1 = paramExpr.visit( this );
				
				applyExpr1.putAssign( nameExpr, paramExpr1 );
			}
		}
		catch( NotBoundException e ) {
			throw new RuntimeException( e );
		}
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor accept ApplyExpr: Trying to push rest bindings." );		
				
		// try to push the rest
		/* try {

			applyExpr1.attemptPushRest();
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor accept ApplyExpr: Push of rest bindings successful or none present." );		
			
		}
		catch( NotDerivableException e ) {

			// if it does not work in this round, it may still work in the next
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor accept ApplyExpr: Push of rest bindings not successful. It may still work later." );		
		} */
		
		if( taskExpr1.getNumSingleExpr() == 1 ) {
			
			// task expression is single expression
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor accept ApplyExpr: Task expression is single expression." );		

			try {
				
				if( taskExpr1.getNumAtom() == 1 ) {
					
					// task expression is single value
					if( log.isTraceEnabled() )
						log.trace( "DynamicNodeVisitor accept ApplyExpr: Task expression is single value." );		
					
					se = taskExpr1.getSingleExpr( 0 );
					
					if( !( se instanceof LambdaExpr ) ) {
						
						if( log.isTraceEnabled() )
							log.trace( "DynamicNodeVisitor accept ApplyExpr: Singular task expression is not a lambda expression. Returning what we have so far: "+applyExpr1.toString().replace( '\n', ' ' ) );		

						return new CompoundExpr( applyExpr1 );
					}
					
					// combine parameters
					return combineParam( applyExpr1 );
					
				}
					
				// task expression is single expression but multiple value
				return reducePotentiallyCorrelated( applyExpr1 );
			}
			catch( NotDerivableException e ) {

				// cardinality cannot be derived yet
				if( log.isTraceEnabled() )
					log.trace( "DynamicNodeVisitor accept ApplyExpr: Cardinality cannot be derived yet. Returning what we have so far: "+applyExpr.toString().replace( '\n', ' ' ) );		

				return new CompoundExpr( applyExpr1 );
			}
		}
			
		// task expression is multiple
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor accept ApplyExpr: Task expression is multiple." );		
				
		return reducePotentiallyCorrelated( applyExpr1 );

	}

	
	
	
	@Override
	public CompoundExpr accept( CompoundExpr ce ) throws HasFailedException, NotBoundException {
		
		CompoundExpr result, intermediate;
		
		if( ce == null )
			throw new NullPointerException( "Compound expression must not be null." );

		result = new CompoundExpr();
		
		for( SingleExpr singleExpr : ce.getSingleExprList() ) {
		
			intermediate = singleExpr.visit( this );
			if( intermediate == null )
				throw new NullPointerException( "Evaluation of single expression must not result in null." );
			
			result.addCompoundExpr( intermediate );
			
		}

		return result;
	}

	@Override
	public CompoundExpr accept( CurryExpr curryExpr ) throws HasFailedException, NotBoundException {
			
			Prototype originalPrototype;
			SingleExpr se;
			LambdaExpr lambdaExpr;
			NativeLambdaExpr nativeLambdaExpr;
			Block originalBodyBlock;
			Block curriedBodyBlock;
			Prototype curriedPrototype;
			NativeLambdaExpr curriedLambdaExpr;
			
			if( !curryExpr.hasTaskExpr() )
				throw new SemanticModelException(
					curryExpr.toString(),
					"Task parameter not bound." );
			
			if( curryExpr.getTaskExpr().getNumSingleExpr() == 0 )
				throw new SemanticModelException(
					curryExpr.toString(),
					"Task expression must not be nil." );
			
			if( curryExpr.getTaskExpr().getNumSingleExpr() > 1 )
				return new CompoundExpr( curryExpr );
			
			se = curryExpr.getTaskExpr().visit( this ).getSingleExpr( 0 );
			
			if( se instanceof NameExpr )
				return new CompoundExpr( curryExpr );
			
			if( !( se instanceof LambdaExpr ) )
				throw new SemanticModelException( curryExpr.toString(),
					se+" is not a lambda expression." );
			
		
			lambdaExpr = ( LambdaExpr )se;
					
			originalPrototype = lambdaExpr.getPrototype();
			
			// the prototype of the curried lambda expression is derived from
			// the original prototype
			curriedPrototype = new Prototype( originalPrototype );
			
			// from the prototype we remove all inputs that are bound by
			// currying
			for( NameExpr nameExpr : curryExpr.getNameSet() )
				curriedPrototype.removeParam( nameExpr );
			
			// TODO: Foreign lambda expressions can of course also be curried
			// TODO: Make sure that the remaining parameters and all output
			//       variables in the prototype are uncorrelated reduce
			if( !( lambdaExpr instanceof NativeLambdaExpr ) )
				throw new UnsupportedOperationException( "NYI. Only native lambda expression can be curried." );
				
			nativeLambdaExpr = ( NativeLambdaExpr )lambdaExpr;
			
			originalBodyBlock = nativeLambdaExpr.getBodyBlock();
						
			// the body block of the curried lambda expression is derived from the
			// body block of the original lambda expression
			curriedBodyBlock = new Block();
			for( NameExpr ne : originalBodyBlock.getFullNameSet() )
				curriedBodyBlock.putAssign( ne, new CompoundExpr( originalBodyBlock.getExpr( ne ) ) );

			// with the curried expression's binding block merged in
			try {
				for( NameExpr nameExpr : curryExpr.getNameSet() )
					curriedBodyBlock.putAssign(
						nameExpr, curryExpr.getExpr( nameExpr ) );
			}
			catch( NotBoundException e ) {
				throw new RuntimeException( e.getMessage() );
			}			
			
			// from the curried prototype and body expression we form the
			// resulting curried lambda expression
			curriedLambdaExpr = new NativeLambdaExpr( curriedPrototype, curriedBodyBlock );

			return new CompoundExpr( curriedLambdaExpr );
			
			// reuse this commented block when in dynamic reducer
			/* if( lambdaExpr instanceof ForeignLambdaExpr ) {
				
				applyExpr = new ApplyExpr( 1, false );
				
				applyExpr.setTaskExpr( new CompoundExpr( lambdaExpr ) );
				
				try {
					
					for( NameExpr nameExpr : originalPrototype.getParamNameExprSet() )
						
						if( curryExpr.containsName( nameExpr ) )
							applyExpr.putAssign( nameExpr, curryExpr.getExpr( nameExpr ) );
						else
							applyExpr.putAssign( nameExpr, new CompoundExpr( nameExpr ) );
				}
				catch( NotBoundException e ) {
					throw new RuntimeException( e.getMessage() );
				}
				
				curriedBodyBlock = new Block();
				
				n = originalPrototype.getNumOutput();
				
				// replicate apply expression for each output name expression
				for( i = 0; i < n; i++  ) {
					
					ne = originalPrototype.getOutput( i );
					
					ae = applyExpr.clone();
					ae.setChannel( i+1 );
					
					curriedBodyBlock.putAssign( ne, new CompoundExpr( ae ) );
				}
				
				curriedLambdaExpr = new NativeLambdaExpr( curriedPrototype, curriedBodyBlock );
				
				return new CompoundExpr( curriedLambdaExpr );			
			} */		

	}
	
	public UUID getQueryId() {
		return queryId;
	}
			
	public void setTopLevelContext( TopLevelContext tlc ) {

		if( tlc == null )
			throw new NullPointerException( "Top level context must not be null." );
		
		currentBlock = tlc;

	}
	
	public void step() {
		
		CompoundExpr ce;
		long tic, toc;
		StringBuffer buf;
		
		if( log.isDebugEnabled() )
			log.debug( "STILL THERE" );
		
		try {
			tic = System.currentTimeMillis();
			
			if( log.isDebugEnabled() )
				log.debug( ">>> Stepping expression >>>\n"+currentBlock );
			
			
			ce = currentBlock.visit( this );
			toc = System.currentTimeMillis();
			
			if( statLog.isDebugEnabled() )
				statLog.debug( new JsonReportEntry( ticketSrc.getRunId(), null, null, null, null, null, JsonReportEntry.KEY_REDUCTION_TIME, String.valueOf( toc-tic ) ) );
			
			if( log.isDebugEnabled() )
				log.debug( "Checking if queue is clear ..." );
			
			if( ticketSrc.isQueueClear( queryId ) ) {
				
				if( log.isDebugEnabled() )
					log.debug( "Queue is clear. Finishing query "+queryId+"." );

				repl.queryFinished( queryId, ce );
				
				return;
			}
			
			if( log.isDebugEnabled() ) {
				
				buf = new StringBuffer();
				
				buf.append( "Queue is not empty. " ).append( queryId ).append( " waits for [ " );
				for( Ticket t : ticketSrc.getTicketSet( queryId ) )
					buf.append( t.getTicketId() ).append( ' ' );
				buf.append( ']' );
				
				log.debug( buf.toString() );
			}
				
			
		}
		catch( Exception e ) {
			repl.queryFailed( queryId, null, e, null, null, null );
		}
	}
	
	public CompoundExpr getCurrentExpr() {
		
		CompoundExpr result;
		TopLevelContext tlc;
		
		if( !( currentBlock instanceof TopLevelContext ) )
			throw new RuntimeException( "Current block is not a top level context." );
		
		tlc = ( TopLevelContext )currentBlock;
		
		result = new CompoundExpr();
		
		for( CompoundExpr ce : tlc.getTargetList() )
			result.addCompoundExpr( ce );
		
		return result;

	}

	private CompoundExpr combineParam( ApplyExpr applyExpr ) throws HasFailedException {
		
		EnumHelper helper;
		int i, n;
		CompoundExpr ce, ce0;
		Block bindingBlock;
		LambdaExpr lambda;
		ApplyExpr singularApplyExpr;
		QualifiedTicket qt;
		
		try {
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor combineParam ApplyExpr: "+applyExpr.toString().replace( '\n', ' ') );
		
			helper = new EnumHelper( applyExpr );
			n = helper.getCardinality();
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Creating CombiHelper with cardinality "+n );
						
			ce = new CompoundExpr();
			
			for( i = 0; i < n; i++ ) {
				
				bindingBlock = helper.getSingularBindingBlock( i ); // throws NotDerivableException
				lambda = helper.getSingularLambdaExpr( i ); // throws NotDerivableException
				
				singularApplyExpr = new ApplyExpr( applyExpr.getChannel(), applyExpr.hasRest() );
				singularApplyExpr.setParamBindMap( bindingBlock.getParamBindMap() );
				singularApplyExpr.setTaskExpr( new CompoundExpr( lambda ) );
				
				if( log.isTraceEnabled() )
					log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Enumerating singular application "+singularApplyExpr.toString().replace( '\n', ' ' ) );
			
				if( lambda instanceof ForeignLambdaExpr ) {
					
					if( log.isTraceEnabled() )
						log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Task expression is foreign." );
				
					if( singularApplyExpr.isParamNormal() ) {
						
						if( log.isTraceEnabled() )
							log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Application parameters are in normal form." );

						qt = ticketSrc.requestTicket( repl, queryId, singularApplyExpr );
						if( qt == null )
							throw new NullPointerException( "Qualified ticket must not be null." );
						
						if( log.isTraceEnabled() )
							log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Ticket requested. Appending qualified ticket: "+qt.toString().replace( '\n', ' ' ) );
												
						ce.addSingleExpr( qt );
						continue;
					}
					
					if( log.isTraceEnabled() )
						log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Application parameters are not in normal form. Appending what we have so far: "+singularApplyExpr.toString().replace( '\n', ' ' ) );
					
					ce.addSingleExpr( singularApplyExpr );
					continue;
				}
				
				
				if( lambda instanceof NativeLambdaExpr ) {
					
					if( log.isTraceEnabled() )
						log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Task expression is native." );

					ce0 = reduceSingleNative( singularApplyExpr );
					
					if( log.isTraceEnabled() )
						log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Appending result of single native reduction step: "+ce0.toString().replace( '\n', ' ' ) );

					ce.addCompoundExpr( ce0 );
					continue;
				}
				
				throw new RuntimeException( "Lambda expression type not recognized." );
			}
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Combination complete; returning: "+ce.toString().replace( '\n', ' ' ) );			
			
			return ce;
		}
		catch( NotDerivableException e ) {
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor combineParam ApplyExpr: Some information could not be derived. Returning original: "+applyExpr.toString().replace( '\n', ' ' ) );
					
			return new CompoundExpr( applyExpr );
		}
	}

	private void popBlock() {		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor popping out of block." );

		currentBlock = blockStack.pop();
	}

	private void pushIntoBlock( Block block ) {
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor pushing into block." );

		
		blockStack.push( currentBlock );
		currentBlock = block;
	}
	
	private CompoundExpr reducePotentiallyCorrelated( ApplyExpr applyExpr ) throws HasFailedException, NotBoundException {
		
		CompoundExpr taskExpr, ce, ce0;
		SingleExpr se;
		LambdaExpr lambda;
		Prototype prototype;
		ApplyExpr applyExpr1;
		int i, n;
		
	
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: "+applyExpr.toString().replace( '\n', ' ' ) );

		
		taskExpr = applyExpr.getTaskExpr();
		se = taskExpr.getSingleExpr( 0 );
		
		if( !( se instanceof LambdaExpr ) ) {
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Task expression is not a lambda expression. Returning original: "+applyExpr.toString().replace( '\n', ' ' ) );
			
			return new CompoundExpr( applyExpr );
		}
		
		lambda = ( LambdaExpr )se;
		prototype = lambda.getPrototype();
		
		if( prototype.isTaskCorrelated() ) {
			
			// task parameter is correlated
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Prototype is task-correlated." );
			
			// combine and create tickets
			
			return  combineParam( applyExpr );
		}
		
		// enumerate task expression and create an application from each
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Prototype is not task-correlated.Enumerate task expressions and create application for each." );
		
		try {	
			n = taskExpr.getNumAtom();
		}
		catch( NotDerivableException e ) {
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Could not derive cardinality of task expression. Returning original: "+applyExpr.toString().replace( '\n', ' ' ) );

			return new CompoundExpr( applyExpr );
		}
		
		ce = new CompoundExpr();
		
		for( i = 0; i < n; i++ ) {
			
			applyExpr1 = new ApplyExpr( applyExpr );
			
			// TODO: This won't work if the task expression was the result of an output-reduce task
			ce0 = new CompoundExpr( taskExpr.getSingleExpr( i ) );
			
			try {
				if( ce0.getNumAtom() != 1 )
					throw new RuntimeException( "Enumeration resulted in non-singular expression: "+ce0 );
			} catch (NotDerivableException e) {
				throw new RuntimeException( e );
			}
			
			applyExpr1.setTaskExpr( ce0 );
			
			if( log.isTraceEnabled() )
				log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Appending "+applyExpr.toString().replace( '\n', ' ' ) );
			
			ce.addSingleExpr( applyExpr1 );
		}
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Trying to reduce "+ce.toString().replace( '\n', ' ' ) );
		
		ce0 = ce.visit( this );
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor reducePotentiallyCorrelated ApplyExpr: Returning "+ce0.toString().replace( '\n', ' ' ) );		
		
		return ce0;

	}
	
	private CompoundExpr reduceSingleNative( ApplyExpr applyExpr ) throws HasFailedException {
		
		CompoundExpr taskResult;
		SingleExpr se;
		NativeLambdaExpr lambda;
		NameExpr targetNameExpr;
		int channel;
		CompoundExpr targetCompoundExpr, ce;
		ApplyExpr applyExpr1;
		CompoundExpr lamList;
		boolean push;
		Block body, originalBody;
		Prototype sign;
		Block binding;
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor reduceSingleNative ApplyExpr: "+applyExpr.toString().replace( '\n', ' ' ) );

		
		taskResult = applyExpr.getTaskExpr();
		try {
			if( taskResult.getNumAtom() != 1 )
				throw new RuntimeException( "Expected application with singular task expression" );
		}
		catch( NotDerivableException e2 ) {
			throw new RuntimeException( "Cannot derive cardinality of task expression." );
		}
		
		channel = applyExpr.getChannel();
		push = applyExpr.hasRest();
		
		// fetch that lambda expression
		se = taskResult.getSingleExpr( 0 );
		
		if( se == null )
			throw new NullPointerException( "Single expression must not be null." );
		
		// continue only if that single lambda expression is native
		if( !( se instanceof NativeLambdaExpr ) )
			throw new RuntimeException( "Single native lambda expression expected." );
			
		
		lambda = ( NativeLambdaExpr )se;
		applyExpr.setParent( lambda.getBodyBlock() );

			
		try {
			pushIntoBlock( applyExpr.getParamBlock() );
		} catch( NotDerivableException e ) {
			throw new RuntimeException( e );
		}
		
		targetNameExpr = lambda.getPrototype().getOutput( channel-1 );

		try {
			
			if( log.isDebugEnabled() )
				log.debug( "Visiting "+currentBlock.getExpr( targetNameExpr ) );
			
			System.out.println( currentBlock );
			
			
			targetCompoundExpr = currentBlock.getExpr( targetNameExpr ).visit( this );
			if( targetCompoundExpr == null )
				throw new NullPointerException( "Evaluation of target expression must not result in null." );
		}
		catch( NotBoundException e1 ) {
			throw new SemanticModelException( applyExpr.toString(), e1.getMessage() );
		}
		popBlock();
		
		if( log.isTraceEnabled() )
			log.trace( "DynamicNodeVisitor reduceSingleNative ApplyExpr: Returning "+targetCompoundExpr.toString().replace( '\n', ' ' ) );

		if( targetCompoundExpr.isNormal() )
			return targetCompoundExpr;
		
	
		
		sign = lambda.getPrototype();
		

		originalBody = lambda.getBodyBlock();
		
		try {
			body = new Block();
			for( NameExpr ne : originalBody.getFullNameSet() )
				body.putAssign( ne, new CompoundExpr( originalBody.getExpr( ne ) ) );
		} catch (NotBoundException e) {
			throw new RuntimeException( e );
		}
		
		if( log.isDebugEnabled() )
			log.debug( "I DO LOG SOMETHING" );
		
		if( log.isDebugEnabled() ){
			log.debug( "Replacing "+targetNameExpr );
			log.debug( "With "+targetCompoundExpr );
		}
		
		
		
		body.putAssign( targetNameExpr, targetCompoundExpr );
					
		lamList = new CompoundExpr( new NativeLambdaExpr( sign, body ) );
		
		binding = new Block();
		for( NameExpr ne : applyExpr.getParamBindMap().keySet() ) {
			
			ce = applyExpr.getParamBindMap().get( ne );
			
			binding.putAssign( ne, new CompoundExpr( ce ) );
		}
		
		
		applyExpr1 = new ApplyExpr( channel, push );
		applyExpr1.setTaskExpr( lamList );
		applyExpr1.setParamBindMap( binding.getParamBindMap() );
		
		
		return new CompoundExpr( applyExpr1 );


	}

	@Override
	public CompoundExpr accept( TopLevelContext tlc ) throws HasFailedException, NotBoundException {
		
		CompoundExpr result;
		
		
		result = new CompoundExpr();
		
		for( CompoundExpr ce : tlc.getTargetList() )
			result.addCompoundExpr( ce.visit( this ) );
		
		tlc.clearTargetList();
		tlc.addTarget( result );
				
		return result;
	}


}
