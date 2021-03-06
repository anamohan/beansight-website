package models.analytics;

import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

import org.joda.time.DateMidnight;
import org.joda.time.DateTime;

import models.Insight;
import models.User;
import models.Vote;
import play.db.jpa.Model;

/**
 * The total number of created Insights in a certain day
 */
@Entity
public class DailyTotalInsight extends Model {

	@Transient
	private static Date NO_ANALYTIC_BEFORE_DATE = new DateMidnight(2011, 1, 26).toDate();
	
	/** the date for which this analytics has been created */
	public Date forDate;
	
	public long insightCount;
	
	public DailyTotalInsight(Date forDate, long voteCount) {
		super();
		this.forDate = forDate;
		this.insightCount = voteCount;
	}
	
	public static boolean findIfAnalyticExistsForDate(Date date) {
		// there is no prediction before 26 january 2011
		if (date.before(NO_ANALYTIC_BEFORE_DATE)) {
			return true;
		}
		if (count("forDate = ?", date) > 0) {
			return true;
		}
		return false;
	}
	
	
    public static void compute(DateTime startOfDayForCalculation) {
    	if (DailyTotalInsight.findIfAnalyticExistsForDate(startOfDayForCalculation.toDate()) == true) {
    		return; // stop
    	}
    	DateTime endOfDayForCalculation = startOfDayForCalculation.plusDays(1).minusSeconds(1);
		
    	long dailyCount = Insight.count("creationDate between ? and  ?", startOfDayForCalculation.toDate(), endOfDayForCalculation.toDate());
		
    	DailyTotalInsight analytic = new DailyTotalInsight(startOfDayForCalculation.toDate(), dailyCount);
		analytic.save();
		
		// recursive call to create previous records if they don't exist yet
		startOfDayForCalculation = startOfDayForCalculation.minusDays(1);
		compute(startOfDayForCalculation);
    }
	
}
