# Observable composer

[Documentation home](../README.md) · [Worldview explorer](WORLDVIEW.md)

The composer helps you build semantic expressions from concepts, operators,
literal values, and authority identities. A connected Reasoner proposes compatible
next components. The colored expression is confirmed content; the text field at
its end is your pending search or value.

## Open the composer

- In a source editor, press **Ctrl+Shift+Space**. Accepting inserts the expression
  at the invoking cursor as one undoable edit.
- In a digital-twin control panel, use the concept icon. Accepting submits an
  observation request to that twin, subject to its context and geometry.
- In the Worldview locator, press **Shift+Space** to compose and explore a concept.

Escape or closing the overlay dismisses it without accepting. Inserting source
does not submit an observation. If source changes while composition is pending,
the late result is discarded rather than inserted into a changed document.

> **Screenshot placeholder — Composer:** annotate the source chooser, proposed
> concepts/operators, confirmed colored expression, pending query, concept card,
> copy icon, and acceptance arrow. Include an incomplete clause with its explanation.

## Build an expression

1. Type at least two characters to search for a concept. Symbol operators and
   requested literal values can use shorter input.
2. Use **Up/Down** to highlight a result. Inspect its concept card and restrictions.
3. Press **Enter** at the end of the query, or double-click the result, to add it.
4. Continue with the Reasoner's next proposals until the expression says what you need.
5. Clear any unconfirmed query, then use the arrow or **Ctrl+Enter** in the input
   to accept. The adjacent copy icon copies the exact confirmed declaration.

The confirmed prefix is edited through semantic operations. Ordinary typing edits
the pending text. Direct restrictions have a label icon; inherited restrictions
have a merge icon. Their tooltips explain the distinction.

| Key or action | Effect |
| --- | --- |
| Enter on a result | Add the selected concept/operator and return to the input |
| Enter in the input | Accept a requested literal, or add the highlighted result when the cursor is at the end of a nonblank query |
| Space after an exact result name or identifier | Add the highlighted exact match; partial searches and literal values keep ordinary spaces |
| Backspace at the input boundary | Undo one confirmed step; elsewhere it edits pending text |
| `(` or `)` in concept mode | Open or close a group when permitted; literals and authority searches keep these as text |
| Ctrl+Enter in the input | Accept confirmed content when no query or request remains pending |
| Escape | Dismiss without accepting |

Holding a confirmation or undo key does not repeat semantic edits. Ctrl+Enter on
the results table is ignored. The arrow can accept a confirmed declaration such
as a predicate; the receiving view checks whether it can use that declaration.

## Search an authority

Choose **Authorities** in the source chooser, then select a searchable worldview-local
authority ID. Type at least two characters. Results show identity labels and
canonical codes on the left, with documentation on the right. Selecting a row
loads its documentation; Enter or double-click inserts the identity as one
undoable step. Switching modes keeps the expression and pending query.

The chooser contains configured searchable authorities for your loaded worldview.
When offered, use the adjacent filter to restrict search to a catalog, taxonomic
rank, or codelist; **All identities** searches across them. Results include aliases.
Previously seen results may appear while the current search checks for updates;
wait for that check before choosing an identity.

An inserted identity with an approved codelist name has a swap icon. Toggle it
to choose the approved name or raw authority code; both refer to the same identity.
Copy and acceptance use the displayed form.

An identity can be browsed on another host, but insertion also requires that binding
on the Reasoner composing the expression. If results are truncated, refine your
query. Other documentation formats may open as links and require browser sign-in.

> **Screenshot placeholder — Authority search:** show the local authority ID,
> identity results, documentation pane, and a confirmed authority token.

## Submit to a digital twin

Qualities need a context observation and use its geometry. Substantials become
collective requests using the selected observer's perceived extent. A predicate
qualified with an inherent can also be adapted: a substantial inherent becomes
collective, while a quality inherent uses the mandatory context observation.
These adaptations do not alter the expression displayed in the composer.

Missing context, observer, or geometry is reported before dispatch. Once submitted,
the twin's **Activities** view follows progress and offers cancellation for supported
jobs. See [Digital twins and observations](DIGITAL_TWINS.md).

## Recover from a slow or failed request

The input icon shows a spinner and an operation status while work runs. Confirmed
tokens remain visible. You can type the next query during insertion; wait for the
confirmed result before accepting the expression.

After a transport failure, use **Retry search** to recover the current state.
Do not assume a failed reply means the edit was rejected. Uncertain state cannot
be submitted. A timeout during an edit or an expired session can require an explicit
restart using the input icon or boundary Backspace. Restart clears the confirmed
expression and preserves pending text. Copy any expression you want to keep first
when copying is available. A rejected literal keeps its diagnostic and pending
text so you can correct it.
