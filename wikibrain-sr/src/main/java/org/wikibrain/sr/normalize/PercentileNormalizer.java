package org.wikibrain.sr.normalize;

import com.typesafe.config.Config;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.wikibrain.conf.Configuration;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.utils.WbMathUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;


/**
 * This class is called percentile normalizer, but it returns normalized values in [0,1].
 */
public class PercentileNormalizer extends BaseNormalizer {
    protected transient PolynomialSplineFunction interpolator;

    @Override
    public void reset() {
        super.reset();
        interpolator = null;
    }

    @Override
    public void observationsFinished() {
        super.observationsFinished();
        makeInterpolater();
    }

    protected void makeInterpolater() {
        TDoubleArrayList X = new TDoubleArrayList();
        TDoubleArrayList Y = new TDoubleArrayList();

        for (int i = 0; i < sample.size(); i++) {
            double fudge = max * 10E-8 * i;    // ensures monotonic increasing
            X.add(sample.get(i) + fudge);
            Y.add((i + 1.0) / (sample.size() + 1));
        }

        interpolator = new LinearInterpolator().interpolate(X.toArray(), Y.toArray());
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        makeInterpolater();
    }

    @Override
    public double normalize(double x) {
        double sMin = sample.get(0);
        double sMax = sample.get(sample.size() - 1);
        double halfLife = (sMax - sMin) / 4.0;
        double yDelta = 1.0 / (sample.size() + 1);

        if (x < sMin) {
            return WbMathUtils.toAsymptote(sMin - x, halfLife, yDelta, 0.0);
        } else if (x > sMax) {
            return WbMathUtils.toAsymptote(x - sMax, halfLife, 1.0 - yDelta, 1.0);
        } else {
            return interpolator.value(x);
        }
    }

    @Override
    public String dump() {
        StringBuffer buff = new StringBuffer("percentile normalizer: ");
        for (int i = 0; i <= 20; i++) {
            int p = i * 100 / 20;
            int index = p * sample.size() / 100;
            index = Math.min(index, sample.size() - 1);
            buff.append(p + "%: ");
            buff.append(sample.get(index));
            buff.append(", ");
        }
        return buff.toString();
    }

    public static class Provider extends org.wikibrain.conf.Provider<PercentileNormalizer> {
        public Provider(Configurator configurator, Configuration config) throws ConfigurationException {
            super(configurator, config);
        }

        @Override
        public Class getType() {
            return Normalizer.class;
        }

        @Override
        public String getPath() {
            return "sr.normalizer";
        }

        @Override
        public Scope getScope() {
            return Scope.INSTANCE;
        }

        @Override
        public PercentileNormalizer get(String name, Config config, Map<String, String> runtimeParams) throws ConfigurationException {
            if (!config.getString("type").equals("percentile")) {
                return null;
            }

            return new PercentileNormalizer(
            );
        }

    }
}
