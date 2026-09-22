# Migration file format

Hashira migration file is a CQL text file holding one or more statements, with special comment
lines defining attributes and structure.

The file must be encoded in UTF-8.

The file name has no effect on the resulting migration data, but it is advised to keep `.cql`
extension for the migration files.

## Examples

### Reversible multi-statement migration

`01_create_users_groups.cql`:

```
-- description: creates users and groups tables
-- authoredAt: 1469630066000

-- up:

-- stage: 1
CREATE TABLE groups (id uuid PRIMARY KEY, name text)

-- stage: 2
CREATE TABLE users (id uuid PRIMARY KEY, group_id uuid)

-- down:

-- stage: 1
DROP TABLE users

-- stage: 2
DROP TABLE groups
```

### Irreversible single-statement migration

`02_add_users_email.cql`:

```
-- description: adds email to users table
-- authoredAt: 2016-08-01T10:00:00Z

-- up:

ALTER TABLE users
ADD email text
```

### Reversible migration with no-op revert

`03_add_users_last_login_at.cql`:

```
-- description: adds last_login_at to users table
-- authoredAt: 2016-08-02T09:30:00Z

-- up:

ALTER TABLE users
ADD last_login_at timestamp

-- down:
```

## Lines

The format operates in lines:

- **Blank** (empty or whitespace-only): discarded everywhere, including inside CQL statements.
- **Directive line**: `-- <name>:<value>`, starting at the first column, with exactly one space
  after `--`, with `<name>` from the table below (case-sensitive). The value is trimmed.
- **CQL line**: any other line, including comments that are not exact directives, such as
  `--description: x`, `  -- down:` or `-- DOWN:`.

| Directive         | Meaning                                         | Placement                     | Value                         |
|-------------------|-------------------------------------------------|-------------------------------|-------------------------------|
| `-- description:` | defines migration key attribute                 | before `-- up:`, exactly once | non-empty text                |
| `-- authoredAt:`  | defines migration key attribute                 | before `-- up:`, exactly once | see [authoredAt](#authoredat) |
| `-- up:`          | start of the up section                         | exactly once                  | ignored                       |
| `-- down:`        | start of the down section                       | after `-- up:`, at most once  | ignored                       |
| `-- stage:`       | start of a statement in multi-statement up/down | after `-- up:`                | ignored                       |

Any other placement of a directive is an error.

## Structure

- **Preamble**: before `-- up:`.
    - Defines migration key attributes.
    - CQL lines here are ignored.
    - `-- description:` and `-- authoredAt:` must be defined exactly once.
- **Up**: from `-- up:` to `-- down:` or the end of the file.
    - Required and must contain at least one statement.
    - If no `-- stage:` defined in the section, all CQL lines there are glued in a single
      CQL statement.
    - `-- stage:` directives inside the section define beginning of statements in a multi-statement
      up section.
- **Down**: from `-- down:` to the end of the file.
    - If absent: the migration is irreversible. Attempting to revert it should result in an error.
    - Present but empty (blank lines only): the migration is reversible, and reverting it is
      a no-op. A comment line makes the section non-empty: with no `-- stage:`, the comment
      becomes a down statement sent to Cassandra.
    - Statements are defined in the same way as for the **Up** section.

A stage must not be empty.

Comment lines (`-- ...`, indentation allowed) are allowed between `-- up:` / `-- down:` and its
first `-- stage:` line. Those comment lines are discarded. Any other CQL line there is an error:
a section should either have no `-- stage:` at all, or have all its statements in stages.

All lines of a CQL statement are joined together with `\n` and passed to Cassandra as is, as a
single query (not split on `;`). So each stage, or a section without stages, must hold exactly one
CQL statement.

Statements are supposed to be run in file order,
with down stages not reversed automatically - write them in undo order.

## authoredAt

`authoredAt` is a timestamp, defined as milliseconds since the Unix epoch.

Valid range: `1 .. 2^63-1`

Two formats are allowed for the attribute:

- an integer: digits with an optional leading `+`, leading zeros allowed, e.g. `1370028263`,
  `+01370028263`;
- an ISO-8601 instant with at most millisecond precision, e.g. `2013-05-31T18:47:42Z`,
  `2013-05-31T18:47:42.999Z`.
