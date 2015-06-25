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

package de.huberlin.wbi.cuneiform.core.semanticmodel;


public class CondExpr implements SingleExpr {

	private final CompoundExpr ifExpr;
	private final CompoundExpr thenExpr;
	private final CompoundExpr elseExpr;
	
	public CondExpr( CompoundExpr ifExpr, CompoundExpr thenBlock, CompoundExpr elseBlock ) {

		if( thenBlock == null )
			throw new IllegalArgumentException( "Then expression must not be null." );
		
		if( elseBlock == null )
			throw new IllegalArgumentException( "Else expression must not be null." );
		
		if( ifExpr == null )
			throw new IllegalArgumentException( "Condition expression must not be null." );
		
		this.ifExpr = ifExpr;
		this.elseExpr = elseBlock;
		this.thenExpr = thenBlock;

	}
	
	public CondExpr(CondExpr template ) {
		
		if( template == null )
			throw new IllegalArgumentException( "Template conditional expression must not be null." );
		
		ifExpr = new CompoundExpr( template.ifExpr );
		thenExpr = new CompoundExpr( template.thenExpr );
		elseExpr = new CompoundExpr( template.elseExpr );
	}

	
	public CompoundExpr getElseExpr() {
		return elseExpr;
	}
	
	public CompoundExpr getIfExpr() {
		return ifExpr;
	}
	
	public CompoundExpr getThenExpr() {
		return thenExpr;
	}
	
	@Override
	public String toString() {
		
		StringBuffer buf;
		
		buf = new StringBuffer();
		
		buf.append( "if " );
		buf.append( ifExpr );
		buf.append( " then " ).append( thenExpr );
		buf.append( " else " ).append( elseExpr ).append( " end" );
		
		return buf.toString();
	}

	@Override
	public int getNumAtom() throws NotDerivableException {
		throw new NotDerivableException( "Cannot derive cardinality of reduce output variable." );		
	}
	
	@Override
	public <T> T visit( NodeVisitor<? extends T> visitor ) throws HasFailedException, NotBoundException {
		return visitor.accept( this );
	}

	@Override
	public StringExpr getStringExprValue( int i ) throws NotDerivableException {
		throw new NotDerivableException( "Cannot derive value for conditional expression." );
	}
}
