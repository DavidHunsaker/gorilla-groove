-- MySQL continues to disappoint me, and requires that you update the values prior to running the NOT NULL ALTER, even though the column has a DEFAULT...
UPDATE track SET genre = '' WHERE genre IS NULL;
UPDATE track SET note = '' WHERE note IS NULL;

ALTER TABLE track MODIFY genre varchar(128) NOT NULL DEFAULT '';
ALTER TABLE track MODIFY note varchar(256) NOT NULL DEFAULT '';
