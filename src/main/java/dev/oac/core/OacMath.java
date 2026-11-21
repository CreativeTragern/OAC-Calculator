package dev.oac.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java port of the supplied OAC Calculator.py calculation functions.
 * No Minecraft or external dependencies: the formula can be tested in isolation.
 *
 * Values/trim tables and coordinate semantics are preserved from the Python source.
 * This is a calculator, not an entity cannon, automation or targeting mod.
 */
public final class OacMath {
    public static final int NUKE_LAYER = 15;

    private static final double POS_ARROW = 8.68011400560981;
    private static final double POS_ARROW_OFFSET = 0.00000007953368448855258;
    private static final double POS_ARROW_FINE = 0.10544106641170162;
    private static final double NEG_ARROW = 8.7410329553163;
    private static final double NEG_ARROW_FINE = 0.10136464745734321;
    private static final double NEG_ARROW_OFFSET = 0.00000008400136539190007;
    private static final double POS_TNT = 8.67362335175755 * 0.9;
    private static final double NEG_TNT = 8.733855372210195 * 0.9;
    private static final double POS_FINE = 0.010112727575963596 * 0.9;
    private static final double NEG_FINE = 0.010335220217364634 * 0.9;
    private static final double SIDE_OFFSET = 0.796378035410971 * 0.9 - 0.49;

    private static final double[] TRIM_ARROW = {
        0, 0.8741975000350434, 1.7474285006966748,
        2.619571409382498, 3.4904892604361533, 4.360027759624948,
        5.228013079297519, 6.094249372958224, 6.958515974326133,
        7.8205642419025185
    };
    private static final double[] TRIM_TNT = {
        0, 0.8734785455679479 * 0.9, 1.7460027262632432 * 0.9,
        2.6174521195454687 * 0.9, 3.4876909994406082 * 0.9,
        4.356566374898484 * 0.9, 5.223905775957048 * 0.9,
        6.089514756059614 * 0.9, 6.953174075261127 * 0.9,
        7.814636525118937 * 0.9
    };
    private static final double[] TRIM_FINE_TNT = {
        0, 0.0010329740280212048 * 0.9, 0.002071539746945771 * 0.9,
        0.003116319961532217 * 0.9, 0.004167994321588394 * 0.9,
        0.00522730051831248 * 0.9, 0.006295033922140227 * 0.9,
        0.00737204495600737 * 0.9, 0.00845923325015693 * 0.9,
        0.009557537293892677 * 0.9
    };
    private static final double[] TRIM_FINE_ARROW = {
        0, 0.01012996658256387, 0.02032642404043858,
        0.030597266276200213, 0.04095125892158624,
        0.05139811598717341, 0.06194857676660194,
        0.0726144802414717, 0.08340883302250142,
        0.0943458652197993
    };

    public enum AccelType { ARROW, TNT, FINE, ARROW_FINE }

    private OacMath() {}

    /** Python intC(): truncate to 0 unless within 0.000001 of the next integer. */
    private static int intC(double x) {
        if (!Double.isFinite(x) || x > Integer.MAX_VALUE - 2 || x < Integer.MIN_VALUE + 2) {
            throw new IllegalArgumentException("Coordinate/velocity is out of range");
        }
        int n = (int) x;
        return Math.abs(x - n) > 0.999999 ? n + (x < 0 ? -1 : 1) : n;
    }

    private static double intC1(double x) { return intC(x * 10.0) / 10.0; }

    /** Python's round is half-to-even; Math.rint reproduces this for scaled doubles. */
    private static double round(double x, int places) {
        double m = Math.pow(10, places);
        return Math.rint(x * m) / m;
    }

    private static int pythonIndex(int idx, int len) {
        int actual = idx < 0 ? len + idx : idx;
        if (actual < 0 || actual >= len) throw new IllegalArgumentException("Invalid trim index: " + idx);
        return actual;
    }

    private static int closest(double[] values, double x) {
        int result = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            double diff = Math.abs(values[i] - x);
            if (diff < best) { best = diff; result = i; }
        }
        return result;
    }

    private static int floorTrimIndex(double[] values, double x) {
        int result = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= x) result = i;
        }
        if (result < 0) throw new IllegalArgumentException("Unsupported fine acceleration value");
        return result;
    }

    public static double getCount(double diff, AccelType type) {
        if (type != AccelType.ARROW_FINE) {
            final double coarse;
            final double[] trim;
            if (type == AccelType.ARROW) { coarse = POS_ARROW; trim = TRIM_ARROW; }
            else if (type == AccelType.TNT) { coarse = POS_TNT; trim = TRIM_TNT; }
            else { coarse = POS_FINE; trim = TRIM_FINE_TNT; }
            double x = diff - intC(diff / coarse) * coarse;
            return intC(diff / coarse) + closest(trim, x) / 10.0;
        }
        double x = diff - intC(diff / POS_ARROW) * POS_ARROW;
        int motionIndex = floorTrimIndex(TRIM_ARROW, x);
        double motion = TRIM_ARROW[motionIndex];
        double z = diff - (intC(diff / POS_ARROW) * POS_ARROW + motion)
            - intC((x - motion) / POS_ARROW_FINE) * POS_ARROW_FINE;
        return intC(diff / POS_ARROW) + motionIndex / 10.0
            + (intC((x - motion) / POS_ARROW_FINE) + closest(TRIM_FINE_ARROW, z) / 10.0) / 100.0;
    }

    public static double getVelocity(double count, AccelType type) {
        if (type != AccelType.ARROW_FINE) {
            final double coarse;
            final double[] trim;
            if (type == AccelType.ARROW) { coarse = POS_ARROW; trim = TRIM_ARROW; }
            else if (type == AccelType.TNT) { coarse = POS_TNT; trim = TRIM_TNT; }
            else { coarse = POS_FINE; trim = TRIM_FINE_TNT; }
            int whole = intC(count);
            int index = (int) Math.rint(10 * (count - whole));
            return whole * coarse + trim[pythonIndex(index, trim.length)];
        }
        int whole = intC(count);
        int trimIndex = intC(10 * (count - whole));
        double motion = TRIM_ARROW[pythonIndex(trimIndex, TRIM_ARROW.length)];
        int wholeFine = intC((count - whole - trimIndex / 10.0) * 100);
        double remainder = count - whole - trimIndex / 10.0 - wholeFine / 100.0;
        int fineIndex = intC(remainder * 1000);
        return whole * POS_ARROW + motion + wholeFine * POS_ARROW_FINE
            + TRIM_FINE_ARROW[pythonIndex(fineIndex, TRIM_FINE_ARROW.length)];
    }

    private static double negativeVelocity(double count, double accel, double accelFine) {
        return intC1(count) * accel + (count - intC1(count)) * 100 * accelFine;
    }

    private static double negativeCount(double motion, double accel, double accelFine) {
        double base = intC1(motion / accel);
        return base + intC1((motion - base * accel) / accelFine) / 100;
    }

    /** Reproduces the Python two-stage approximation used in motionAddSearch.b(). */
    private static double approximateNegativeMotion(double ideal, double accel, double accelFine) {
        double base = intC1(ideal / accel);
        double count = base + (ideal - base * accel) / accelFine / 100;
        return negativeVelocity(count, accel, accelFine);
    }

    private record SearchResult(double mainCount, double sideCount, double mainVelocity,
                                double sideVelocity, double ratioDiff) {}

    private static SearchResult motionAddSearch(double pVArrow, double sVArrow, double ideal) {
        boolean sameSign = Math.copySign(pVArrow, sVArrow) == pVArrow;
        boolean mainPositive = ideal >= 0;
        boolean sidePositive = sameSign == mainPositive;
        double pAccel = mainPositive ? POS_ARROW : NEG_ARROW;
        double pFine = mainPositive ? POS_ARROW_FINE : NEG_ARROW_FINE;
        double sAccel = sidePositive ? POS_ARROW : NEG_ARROW;
        double sFine = sidePositive ? POS_ARROW_FINE : NEG_ARROW_FINE;

        double bestRatio = Double.POSITIVE_INFINITY;
        double bestPCount = 0, bestSCount = 0;
        double pAdd = 0, sAdd = 0;
        double pCount = round(ideal / pAccel, 3);

        // A guard is added for unexpectedly large debug inputs. Normal cases stop naturally.
        int steps = 0;
        while (Math.abs(pCount) > 0.001) {
            if (++steps > 100_000) throw new IllegalArgumentException("Search exceeded safe iteration limit");
            pAdd = mainPositive ? getVelocity(pCount, AccelType.ARROW_FINE)
                                : negativeVelocity(pCount, pAccel, pFine);
            double sideIdeal = sVArrow / pVArrow * pAdd;
            sAdd = sidePositive
                ? getVelocity(getCount(sideIdeal, AccelType.ARROW_FINE), AccelType.ARROW_FINE)
                : approximateNegativeMotion(sideIdeal, sAccel, sFine);

            double ratio = Math.abs(sVArrow / pVArrow - sAdd / pAdd);
            if (ratio < bestRatio) {
                bestRatio = ratio;
                bestPCount = pCount;
                bestSCount = sidePositive ? getCount(sAdd, AccelType.ARROW_FINE)
                                          : negativeCount(sAdd, sAccel, sFine);
            }
            if (bestRatio <= 0.000001) break;

            pCount = pCount - Math.copySign(0.001, pCount);
            pAdd = mainPositive ? getVelocity(bestPCount, AccelType.ARROW_FINE)
                                : negativeVelocity(bestPCount, pAccel, pFine);
            sAdd = sidePositive ? getVelocity(bestSCount, AccelType.ARROW_FINE)
                                : negativeVelocity(bestSCount, sAccel, sFine);
        }
        return new SearchResult(bestPCount, bestSCount, pAdd, sAdd, bestRatio);
    }

    /** Map keys match the supplied script's result fields for easy verification. */
    public static Map<String, Object> calculate(boolean debug, int nukeLayer,
            double diffX, double diffZ) {
        if (nukeLayer == 0) throw new IllegalArgumentException("Nuke layer cannot be 0");
        if (!Double.isFinite(diffX) || !Double.isFinite(diffZ))
            throw new IllegalArgumentException("Coordinates must be finite numbers");
        if (diffX == 0 && diffZ == 0)
            throw new IllegalArgumentException("Cannon and target cannot have the same position");

        double mainCountX, mainCountZ;
        double xVArrow, zVArrow, xVBaseTnt, zVBaseTnt;
        if (diffX > 0 && diffZ > 0) {
            mainCountX = getCount(diffX, AccelType.TNT);
            mainCountZ = getCount(diffZ, AccelType.TNT);
            xVArrow = getVelocity(mainCountX, AccelType.ARROW) + Math.abs(mainCountZ) * POS_ARROW_OFFSET;
            zVArrow = getVelocity(mainCountZ, AccelType.ARROW) + Math.abs(mainCountX) * POS_ARROW_OFFSET;
            xVBaseTnt = getVelocity(mainCountX, AccelType.TNT);
            zVBaseTnt = getVelocity(mainCountZ, AccelType.TNT);
        } else if (diffX <= 0 && diffZ <= 0) {
            mainCountX = round(diffX / NEG_TNT, 1);
            mainCountZ = round(diffZ / NEG_TNT, 1);
            xVArrow = mainCountX * NEG_ARROW + Math.abs(mainCountZ) * NEG_ARROW_OFFSET;
            zVArrow = mainCountZ * NEG_ARROW + Math.abs(mainCountX) * NEG_ARROW_OFFSET;
            xVBaseTnt = mainCountX * NEG_TNT;
            zVBaseTnt = mainCountZ * NEG_TNT;
        } else if (diffX > 0) {
            if (Math.abs(diffX) >= Math.abs(diffZ)) {
                mainCountX = getCount(diffX, AccelType.TNT);
                double xIdeal = getVelocity(mainCountX, AccelType.ARROW);
                double zIdeal = diffZ / diffX * xIdeal;
                mainCountZ = round(zIdeal / NEG_ARROW, 1);
            } else {
                mainCountZ = round(diffZ / NEG_TNT, 1);
                double zIdeal = mainCountZ * NEG_ARROW;
                double xIdeal = diffX / diffZ * zIdeal;
                mainCountX = getCount(xIdeal, AccelType.ARROW);
            }
            xVArrow = getVelocity(mainCountX, AccelType.ARROW) + Math.abs(mainCountZ) * NEG_ARROW_OFFSET;
            zVArrow = mainCountZ * NEG_ARROW + Math.abs(mainCountX) * POS_ARROW_OFFSET;
            xVBaseTnt = getVelocity(mainCountX, AccelType.TNT);
            zVBaseTnt = mainCountZ * NEG_TNT;
        } else {
            if (Math.abs(diffX) >= Math.abs(diffZ)) {
                mainCountX = round(diffX / NEG_TNT, 1);
                double xIdeal = mainCountX * NEG_ARROW;
                double zIdeal = diffZ / diffX * xIdeal;
                mainCountZ = getCount(zIdeal, AccelType.ARROW);
            } else {
                mainCountZ = getCount(diffZ, AccelType.TNT);
                double zIdeal = getVelocity(mainCountZ, AccelType.ARROW);
                double xIdeal = diffX / diffZ * zIdeal;
                mainCountX = round(xIdeal / NEG_ARROW, 1);
            }
            xVArrow = mainCountX * NEG_ARROW + Math.abs(mainCountZ) * POS_ARROW_OFFSET;
            zVArrow = getVelocity(mainCountZ, AccelType.ARROW) + Math.abs(mainCountX) * NEG_ARROW_OFFSET;
            xVBaseTnt = mainCountX * NEG_TNT;
            zVBaseTnt = getVelocity(mainCountZ, AccelType.TNT);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mainCountX", mainCountX);
        result.put("mainCountZ", mainCountZ);
        if (debug) {
            result.put("xVBaseTnT", xVBaseTnt / 0.9);
            result.put("xVArrow", xVArrow);
            result.put("zVBaseTnT", zVBaseTnt / 0.9);
            result.put("zVArrow", zVArrow);
        }

        double totalSpace = SIDE_OFFSET * 2 / 11;
        int bounceCount;
        double xReduced, zReduced, direction;
        double xAdd, zAdd, xAddCount, zAddCount, fineX, fineZ;
        SearchResult search;
        if (Math.abs(diffX) > Math.abs(diffZ)) {
            bounceCount = (int) Math.ceil(Math.log(SIDE_OFFSET / Math.abs(xVArrow / 2)) / Math.log(0.099));
            xReduced = Math.abs(xVArrow * Math.pow(0.099, bounceCount));
            zReduced = Math.abs(zVArrow * Math.pow(0.099, bounceCount));
            direction = Math.copySign(1.0, SIDE_OFFSET * 2 - (xReduced + totalSpace));
            xAdd = totalSpace * Math.pow(1 / 0.099, bounceCount) / nukeLayer
                * Math.abs(xVArrow) / xVArrow * direction;
            search = motionAddSearch(xVArrow, zVArrow, xAdd);
            xAddCount = search.mainCount();
            zAddCount = search.sideCount();
            xAdd = search.mainVelocity();
            zAdd = search.sideVelocity();
            fineX = (xVArrow + xAdd * nukeLayer / 2.0) * Math.pow(-0.099, bounceCount)
                - Math.abs(xVArrow) / xVArrow * SIDE_OFFSET * Math.pow(-1, bounceCount);
            fineZ = (zVArrow + zAdd * nukeLayer / 2.0) * Math.pow(-0.099, bounceCount)
                - Math.abs(xVArrow) / xVArrow * SIDE_OFFSET * Math.pow(-1, bounceCount)
                * (zVArrow / xVArrow);
        } else {
            bounceCount = (int) Math.ceil(Math.log(SIDE_OFFSET / Math.abs(zVArrow / 2)) / Math.log(0.099));
            xReduced = Math.abs(xVArrow * Math.pow(0.099, bounceCount));
            zReduced = Math.abs(zVArrow * Math.pow(0.099, bounceCount));
            direction = Math.copySign(1.0, SIDE_OFFSET * 2 - (zReduced + totalSpace));
            zAdd = totalSpace * Math.pow(1 / 0.099, bounceCount) / nukeLayer
                * Math.abs(zVArrow) / zVArrow * direction;
            search = motionAddSearch(zVArrow, xVArrow, zAdd);
            zAddCount = search.mainCount();
            xAddCount = search.sideCount();
            zAdd = search.mainVelocity();
            xAdd = search.sideVelocity();
            fineX = (xVArrow + xAdd * nukeLayer / 2.0) * Math.pow(-0.099, bounceCount)
                - Math.abs(zVArrow) / zVArrow * SIDE_OFFSET * Math.pow(-1, bounceCount)
                * (xVArrow / zVArrow) + 0.00607894052332;
            fineZ = (zVArrow + zAdd * nukeLayer / 2.0) * Math.pow(-0.099, bounceCount)
                - Math.abs(zVArrow) / zVArrow * SIDE_OFFSET * Math.pow(-1, bounceCount);
        }

        double fineXCount = fineX > 0 ? getCount(fineX, AccelType.FINE) : round(fineX / NEG_FINE, 1);
        double fineXReal = fineX > 0 ? getVelocity(fineXCount, AccelType.FINE) : fineXCount * NEG_FINE;
        double fineZCount = fineZ > 0 ? getCount(fineZ, AccelType.FINE) : round(fineZ / NEG_FINE, 1);
        double fineZReal = fineZ > 0 ? getVelocity(fineZCount, AccelType.FINE) : fineZCount * NEG_FINE;

        result.put("fineXCount", fineXCount);
        result.put("fineZCount", fineZCount);
        result.put("xVArrowAddCount", xAddCount);
        result.put("zVArrowAddCount", zAddCount);
        result.put("bounceCount", bounceCount);
        if (debug) {
            result.put("relativeDirection", direction);
            result.put("fineX", fineXReal);
            result.put("fineZ", fineZReal);
            result.put("xVArrowAdd", round(xAdd, 13));
            result.put("zVArrowAdd", round(zAdd, 13));
            result.put("xVArrowReduced", round(xReduced, 13));
            result.put("zVArrowReduced", round(zReduced, 13));
            result.put("xVArrowAddReduced", xAdd * Math.pow(-0.099, bounceCount));
            result.put("zVArrowAddReduced", zAdd * Math.pow(-0.099, bounceCount));
            result.put("sideGapX", search.ratioDiff());
            result.put("sideGapZ", SIDE_OFFSET * 2 - (zReduced + totalSpace));
        }

        String sideDirection, correctionDirection;
        double correctionMotion;
        if (Math.abs(xVArrow) < Math.abs(zVArrow)) {
            sideDirection = "Z";
            correctionDirection = "X";
            correctionMotion = (xVArrow * zVBaseTnt / zVArrow) - xVBaseTnt;
        } else {
            sideDirection = "X";
            correctionDirection = "Z";
            correctionMotion = (zVArrow * xVBaseTnt / xVArrow) - zVBaseTnt;
        }

        double correctionCoarse, correctionFine;
        if (correctionMotion > 0) {
            int whole = intC(correctionMotion / POS_TNT);
            double remainder = correctionMotion - whole * POS_TNT;
            int trimIdx = floorTrimIndex(TRIM_TNT, remainder);
            correctionCoarse = whole + trimIdx / 10.0;
            correctionFine = getCount(correctionMotion
                - getVelocity(correctionCoarse, AccelType.TNT), AccelType.FINE);
        } else {
            correctionCoarse = Math.ceil(correctionMotion / NEG_TNT * 10) / 10;
            correctionFine = round((correctionMotion - correctionCoarse * NEG_TNT) / NEG_FINE, 1);
        }
        result.put("sideTnTDirection", sideDirection);
        result.put("vectorCorrectionDirection", correctionDirection);
        result.put("vectorCorrectionCoarseCount", correctionCoarse);
        result.put("vectorCorrectionFineCount", correctionFine);
        if (debug) result.put("vectorCorrectionMotion", correctionMotion / 0.9);

        String directionFlip = correctionDirection.equals("Z") ? "ON" : "OFF";
        String fuseFlip = directionFlip.equals("ON") ? (mainCountX > 0 ? "ON" : "OFF")
            : (mainCountZ > 0 ? "ON" : "OFF");
        result.put("directionFlip", directionFlip);
        result.put("fuseFlip", fuseFlip);
        return result;
    }

    /** Normal mode: caller passes the raw block coordinate of the cannon chunk corner. */
    public static Map<String, Object> calculateNormal(int cannonX, int cannonZ, int targetX, int targetZ) {
        if (cannonX % 16 != 0 || cannonZ % 16 != 0)
            throw new IllegalArgumentException("Cannon X and Z must be divisible by 16");
        double diffX = (double) targetX - ((double) cannonX + 8);
        double diffZ = (double) targetZ - ((double) cannonZ + 8);
        if (Math.hypot(diffX, diffZ) < 100)
            throw new IllegalArgumentException("Target must be at least 100 blocks from the cannon center");
        return calculate(false, NUKE_LAYER, diffX, diffZ);
    }
}
