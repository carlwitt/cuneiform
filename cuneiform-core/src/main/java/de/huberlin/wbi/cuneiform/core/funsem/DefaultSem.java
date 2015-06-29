package de.huberlin.wbi.cuneiform.core.funsem;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class DefaultSem implements Sem {

	public static Expr[] toExprVec( Stream<Expr> stream ) {

		Object[] resultObj;
		Expr[] resultExpr;
		int n;

		resultObj = stream.toArray();

		n = resultObj.length;
		resultExpr = new Expr[n];

		System.arraycopy( resultObj, 0, resultExpr, 0, n );

		return resultExpr;
	}

	private final Map<String, Expr[]> global;
	private final Supplier<Ticket> createTicket;
	private final Map<ChannelRef, Expr[]> fin;

	public DefaultSem(Map<String, Expr[]> global,
			Supplier<Ticket> createTicket, Map<ChannelRef, Expr[]> fin) {

		this.global = global;
		this.createTicket = createTicket;
		this.fin = fin;
	}

	@Override
	public Expr[] accept( StrExpr strExpr, Map<String, Expr[]> rho ) {
		return new Expr[] { strExpr };
	}
	
	@Override
	public Expr[] accept( SelectExpr selectExpr, Map<String, Expr[]> rho ) {
		
		ChannelRef channelRef;
		
		channelRef = new ChannelRef( selectExpr.getChannel(), selectExpr.getRef() );
		
		if( fin.containsKey( channelRef ) )
			return fin.get( channelRef );
		
		return new Expr[] { selectExpr };
	}

	public Supplier<Ticket> getCreateTicket() {
		return createTicket;
	}

	public Map<ChannelRef, Expr[]> getFin() {
		return fin;
	}

	public Map<String, Expr[]> getGlobal() {
		return global;
	}
}
