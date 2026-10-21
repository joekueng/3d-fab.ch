package com.printcalculator.tools;

import java.util.List;

/** Monotone reference model seconds per layer, with estimated preparation kept separate. */
public record QuoteTimeCurve(long startupSeconds, List<Knot> knots) {
    public record Knot(double p2sSecondsPerLayer, double a1SecondsPerLayer) {}

    public QuoteTimeCurve {
        if (startupSeconds < 0 || knots == null || knots.size() < 2) {
            throw new IllegalArgumentException("A timing curve needs startup and at least two knots");
        }
        knots = List.copyOf(knots);
        double previousX = 0, previousY = 0;
        for (Knot knot : knots) {
            if (!Double.isFinite(knot.p2sSecondsPerLayer()) || !Double.isFinite(knot.a1SecondsPerLayer())
                    || knot.p2sSecondsPerLayer() <= previousX || knot.a1SecondsPerLayer() < previousY
                    || knot.a1SecondsPerLayer() <= 0) {
                throw new IllegalArgumentException("Timing curve knots must be positive and monotone");
            }
            previousX = knot.p2sSecondsPerLayer();previousY = knot.a1SecondsPerLayer();
        }
    }

    public long modelSeconds(long nativeModelSeconds, int layers) {
        if (nativeModelSeconds <= 0 || layers <= 0) throw new IllegalArgumentException("Invalid model duration/layers");
        double x = (double)nativeModelSeconds/layers;
        Knot first = knots.getFirst(), last = knots.getLast();
        double y;
        if (x <= first.p2sSecondsPerLayer()) {
            Knot next = knots.get(1);
            double slope = Math.min((next.a1SecondsPerLayer()-first.a1SecondsPerLayer())
                    /(next.p2sSecondsPerLayer()-first.p2sSecondsPerLayer()),
                    first.a1SecondsPerLayer()/first.p2sSecondsPerLayer());
            y = first.a1SecondsPerLayer()+slope*(x-first.p2sSecondsPerLayer());
        } else if (x >= last.p2sSecondsPerLayer()) {
            y = x*last.a1SecondsPerLayer()/last.p2sSecondsPerLayer();
        } else {
            int upper=1;
            while (knots.get(upper).p2sSecondsPerLayer()<x) upper++;
            Knot a=knots.get(upper-1),b=knots.get(upper);
            y=a.a1SecondsPerLayer()+(b.a1SecondsPerLayer()-a.a1SecondsPerLayer())
                    *(x-a.p2sSecondsPerLayer())/(b.p2sSecondsPerLayer()-a.p2sSecondsPerLayer());
        }
        return Math.round(Math.max(0,y)*layers);
    }
}
