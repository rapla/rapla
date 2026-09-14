package org.rapla.viewspec;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Spike (PRD 073/074, discussion 2026-06-20). Proves three claims with the TWO real
 * dhbw view compositions from PRD 074:
 *   (1) a SMALL CLOSED op registry evaluates them — no free-expression engine needed;
 *   (2) an unknown function is REJECTED, not evaluated (the no-eval / no-XSS property);
 *   (3) each composition compiles into a generated, closed GraphQL directive.
 *
 * Self-contained: no rapla deps, no Spring (tier 1). Implements "something similar"
 * to the rapla Function language — a clean bounded subset, not ParsedText itself.
 */
class CompositionDirectiveSpikeTest
{
    // ----- bounded op-tree (the compiled, portable form of a composition) -----
    sealed interface Node permits Lit, Field, Call {}
    record Lit(Object value) implements Node {}
    record Field(String path) implements Node {}
    record Call(String fn, List<Node> args) implements Node {}

    // ----- closed grammar parser: fn(args) | 'literal' | field.path[idx] -----
    static final class Parser
    {
        private final String s;
        private int i;
        private Parser(String s) { this.s = s; }

        static Node parse(String src)
        {
            Parser p = new Parser(src.trim());
            Node n = p.expr();
            p.ws();
            if (p.i != p.s.length())
                throw new IllegalArgumentException("trailing input: " + p.s.substring(p.i));
            return n;
        }

        private Node expr()
        {
            ws();
            if (peek() == '\'')
                return new Lit(quoted());
            String tok = ident();
            ws();
            if (peek() == '(')
            {
                i++; // consume '('
                List<Node> args = new ArrayList<>();
                ws();
                if (peek() != ')')
                {
                    args.add(expr());
                    ws();
                    while (peek() == ',') { i++; args.add(expr()); ws(); }
                }
                expect(')');
                return new Call(tok, args);
            }
            if (tok.matches("-?\\d+")) return new Lit(Long.parseLong(tok));
            if (tok.equals("true") || tok.equals("false")) return new Lit(Boolean.parseBoolean(tok));
            if (tok.isEmpty()) throw new IllegalArgumentException("expected token at " + i);
            return new Field(tok);
        }

        private String ident()
        {
            ws();
            int st = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || "_.:[]".indexOf(s.charAt(i)) >= 0))
                i++;
            return s.substring(st, i);
        }

        private String quoted()
        {
            expect('\'');
            int st = i;
            while (peek() != '\'') i++;
            String v = s.substring(st, i);
            expect('\'');
            return v;
        }

        private char peek() { return i < s.length() ? s.charAt(i) : '\0'; }
        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private void expect(char c) { if (peek() != c) throw new IllegalArgumentException("expected '" + c + "' at " + i); i++; }
    }

    // ----- evaluator over a JSON-ish row (Map / List), CLOSED function registry -----
    static final class Evaluator
    {
        private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

        Object eval(Node n, Object row)
        {
            if (n instanceof Lit l) return l.value();
            if (n instanceof Field f) return resolvePath(f.path(), row);
            if (n instanceof Call c)
            {
                List<Object> a = new ArrayList<>();
                for (Node arg : c.args()) a.add(eval(arg, row));
                return apply(c.fn(), a);
            }
            throw new IllegalStateException();
        }

        private Object apply(String fn, List<Object> a)
        {
            switch (fn)
            {
                case "concat":
                {
                    StringBuilder b = new StringBuilder();
                    for (Object o : a) b.append(str(o));
                    return b.toString();
                }
                case "formatTime":
                    return ((LocalDateTime) a.get(0)).format(HHMM);
                case "formatDate":
                    return ((LocalDateTime) a.get(0)).format(DateTimeFormatter.ofPattern((String) a.get(1)));
                case "coalesce":
                    for (Object o : a) if (o != null && !"".equals(o)) return o;
                    return null;
                case "org.rapla.eventtimecalculator:durationMinutes":
                    return ChronoUnit.MINUTES.between((LocalDateTime) a.get(0), (LocalDateTime) a.get(1));
                default:
                    // The whole security argument in one line: a function not in the
                    // closed registry is rejected, never evaluated. No eval, no XSS.
                    throw new IllegalArgumentException("unknown op rejected by closed registry: " + fn);
            }
        }

        @SuppressWarnings("unchecked")
        private Object resolvePath(String path, Object root)
        {
            Object cur = root;
            for (String seg : path.split("\\."))
            {
                String name = seg;
                Integer idx = null;
                int b = seg.indexOf('[');
                if (b >= 0)
                {
                    name = seg.substring(0, b);
                    idx = Integer.parseInt(seg.substring(b + 1, seg.indexOf(']')));
                }
                if (!(cur instanceof Map)) return null;
                cur = ((Map<String, Object>) cur).get(name);
                if (idx != null)
                {
                    if (cur instanceof List<?> list && idx < list.size()) cur = list.get(idx);
                    else return null;
                }
            }
            return cur;
        }

        private static String str(Object o) { return o == null ? "" : o.toString(); }
    }

    // ----- "generator": a named calculate {as, expr} -> closed directive + op-tree -----
    record GeneratedDirective(String sdl, Node opTree) {}

    static GeneratedDirective generate(String as, String expr)
    {
        Node opTree = Parser.parse(expr); // validates against the closed grammar
        return new GeneratedDirective("directive @" + as + " on FIELD", opTree);
    }

    // ===================== tests over the real PRD 074 compositions =====================

    private Object terminRow()
    {
        Map<String, Object> room = new HashMap<>();
        room.put("number", "A474");
        Map<String, Object> alloc0 = new HashMap<>();
        alloc0.put("room", room);
        Map<String, Object> row = new HashMap<>();
        row.put("start", LocalDateTime.of(2026, 6, 22, 10, 0));
        row.put("end", LocalDateTime.of(2026, 6, 22, 11, 30));
        row.put("allocatables", List.of(alloc0));
        return row;
    }

    @Test
    void timeRangeComposition_evaluates()
    {
        Node n = Parser.parse("concat(formatTime(start),'–',formatTime(end))");
        assertEquals("10:00–11:30", new Evaluator().eval(n, terminRow()));
    }

    @Test
    void roomComposition_returnsValueWhenPresent()
    {
        Node n = Parser.parse("coalesce(allocatables[0].room.number,'—')");
        assertEquals("A474", new Evaluator().eval(n, terminRow()));
    }

    @Test
    void roomComposition_fallsBackWhenAbsent()
    {
        Node n = Parser.parse("coalesce(allocatables[0].room.number,'—')");
        Map<String, Object> empty = new HashMap<>();
        empty.put("allocatables", List.of()); // no room -> coalesce fallback
        assertEquals("—", new Evaluator().eval(n, empty));
    }

    @Test
    void dayComposition_formats()
    {
        Node n = Parser.parse("formatDate(start,'yyyy-MM-dd')");
        assertEquals("2026-06-22", new Evaluator().eval(n, terminRow()));
    }

    @Test
    void durationMinutesPluginOp_computes()
    {
        Node n = Parser.parse("org.rapla.eventtimecalculator:durationMinutes(start,end)");
        assertEquals(90L, new Evaluator().eval(n, terminRow()));
    }

    @Test
    void unknownFunction_isRejectedNotEvaluated()
    {
        Node n = Parser.parse("exec('rm -rf /')");
        assertThrows(IllegalArgumentException.class, () -> new Evaluator().eval(n, terminRow()));
    }

    @Test
    void compositions_generateClosedDirectives()
    {
        assertEquals("directive @timeRange on FIELD",
                generate("timeRange", "concat(formatTime(start),'–',formatTime(end))").sdl());
        assertEquals("directive @room on FIELD",
                generate("room", "coalesce(allocatables[0].room.number,'—')").sdl());
        assertEquals("directive @day on FIELD",
                generate("day", "formatDate(start,'yyyy-MM-dd')").sdl());

        // The generated directive carries the compiled op-tree; the wire only ever
        // sees the bare directive name (@timeRange), never the expression string.
        GeneratedDirective d = generate("timeRange", "concat(formatTime(start),'–',formatTime(end))");
        assertEquals("10:00–11:30", new Evaluator().eval(d.opTree(), terminRow()));
    }
}
