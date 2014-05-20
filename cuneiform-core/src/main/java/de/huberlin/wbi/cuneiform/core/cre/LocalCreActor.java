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

package de.huberlin.wbi.cuneiform.core.cre;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.huberlin.wbi.cuneiform.core.actormodel.Actor;
import de.huberlin.wbi.cuneiform.core.semanticmodel.Ticket;
import de.huberlin.wbi.cuneiform.core.ticketsrc.TicketSrcActor;

public class LocalCreActor extends BaseCreActor {

	private static final int NTHREADS = 8;
	
	private final ExecutorService executor;
	private File buildDir;
	
	public LocalCreActor( File buildDir ) {
		
		executor = Executors.newFixedThreadPool( NTHREADS );
		setBuildDir( buildDir );
	}
	
	@Override
	public synchronized void processMsg( TicketReadyMsg msg ) {
		
		LocalThread localThread;
		Actor sender;
		TicketSrcActor ticketSrc;
		Ticket ticket;
		
		sender = msg.getSender();
		if( !( sender instanceof TicketSrcActor ) )
			throw new RuntimeException( "Ticket source actor expected." );
		
		ticketSrc = ( TicketSrcActor )sender;
		
		ticket = msg.getTicket();
		
		if( !ticket.isNormal() )
			throw new RuntimeException( "Ticket "+ticket.getTicketId()+": Trying to evaluate ticket that is not ready." );
		
		if( ticket.isEvaluated() )
			throw new RuntimeException(
				"Ticket "+ticket.getTicketId()+": Trying to evaluate ticket that has already been evaluated." );
		
		localThread = new LocalThread( ticketSrc, this, ticket, buildDir );
		
		executor.submit( localThread );
		
	}
	
	private void setBuildDir( File buildDir ) {
		
		if( buildDir == null )
			throw new NullPointerException( "Build directory must not be null." );
		
		if( !buildDir.exists() )
			throw new RuntimeException( "Build directory does not exist." );
		
		if( !buildDir.isDirectory() )
			throw new RuntimeException( "Directory expected." );
		
		this.buildDir = buildDir;
	}

	@Override
	protected void shutdown() {
		executor.shutdownNow();
	}

}