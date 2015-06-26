package de.huberlin.wbi.cuneiform.core.funsem;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface Expr {

	public Stream<Expr> step(Map<String, Expr[]> rho,
			Map<String, Expr[]> global, Supplier<Ticket> createTicket,
			Map<RefChannel, Expr[]> fin);
}
