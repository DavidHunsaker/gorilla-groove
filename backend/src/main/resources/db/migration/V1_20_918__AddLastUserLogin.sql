ALTER TABLE `groovatron`.`user`
ADD COLUMN `last_login` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `password`;
