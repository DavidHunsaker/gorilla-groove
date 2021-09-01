-- When sort order was originally added, they were all defaulted to 0
-- This will update the sort order so that it is unique per playlist just based off the row number
CREATE PROCEDURE updateSortOrder()
  BEGIN
    DECLARE current_playlist_id INT;
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE playlist_cursor CURSOR FOR (SELECT id FROM playlist p WHERE deleted = FALSE);
    DECLARE CONTINUE HANDLER FOR SQLSTATE '02000' SET v_done = TRUE;

    OPEN playlist_cursor;

    playlist_loop: LOOP
      FETCH playlist_cursor INTO current_playlist_id;

      IF v_done THEN
        LEAVE playlist_loop;
      END IF;

      SET @rownum := -1;

      UPDATE playlist_track SET sort_order = @rownum := @rownum + 1, updated_at = CURRENT_TIMESTAMP
      WHERE -1 = (@rownum := -1)
        AND deleted = false
        AND playlist_id = current_playlist_id
      ORDER BY sort_order, created_at;

    END LOOP;

    CLOSE playlist_cursor;
  END;

CALL updateSortOrder();
DROP PROCEDURE updateSortOrder;
