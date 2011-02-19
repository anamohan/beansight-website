-- Add to this file the database changes that need to be applied to the %playapps database
-- Reset this page after each release and after having created a SVN tag
--

ALTER TABLE `User`
ADD COLUMN `isPromocodeValidated` bit(1) NOT NULL;

CREATE TABLE  `DailyTotalVote` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `forDate` datetime DEFAULT NULL,
  `voteCount` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8