/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import java.security.SecureRandom;

public final class Recovery {
    public static final int PAYLOAD = 7;
    public static final int LEN = 8;

    private static final int[] D = {
        0,1,2,3,4,5,6,7,8,9,
        1,2,3,4,0,6,7,8,9,5,
        2,3,4,0,1,7,8,9,5,6,
        3,4,0,1,2,8,9,5,6,7,
        4,0,1,2,3,9,5,6,7,8,
        5,9,8,7,6,0,4,3,2,1,
        6,5,9,8,7,1,0,4,3,2,
        7,6,5,9,8,2,1,0,4,3,
        8,7,6,5,9,3,2,1,0,4,
        9,8,7,6,5,4,3,2,1,0
    };
    private static final int[] P = {
        0,1,2,3,4,5,6,7,8,9,
        1,5,7,6,2,8,3,0,9,4,
        5,8,0,3,7,9,6,1,4,2,
        8,9,1,6,0,4,3,5,2,7,
        9,4,5,3,1,2,6,8,7,0,
        4,2,8,6,5,7,3,9,0,1,
        2,7,9,3,8,0,6,4,1,5,
        7,0,4,6,9,1,3,2,5,8
    };
    private static final int[] INV = {0,4,3,2,1,5,6,7,8,9};

    private Recovery() {}

    public static String generate() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(LEN);
        int c = 0;
        for (int i = 0; i < PAYLOAD; i++) {
            int d = r.nextInt(10);
            sb.append((char) ('0' + d));
            int pVal = P[((i + 1) % 8) * 10 + d];
            c = D[c * 10 + pVal];
        }
        sb.append((char) ('0' + INV[c]));
        return sb.toString();
    }

    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(LEN);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    public static boolean valid(String raw) {
        String s = normalize(raw);
        if (s.length() != LEN) return false;
        int c = 0;
        for (int i = 0; i < LEN; i++) {
            int d = s.charAt(LEN - 1 - i) - '0';
            int pVal = P[(i % 8) * 10 + d];
            c = D[c * 10 + pVal];
        }
        return c == 0;
    }

    public static String pretty(String raw) {
        String s = normalize(raw);
        if (s.length() != LEN) return s;
        return s.substring(0, 4) + " - " + s.substring(4);
    }
}
