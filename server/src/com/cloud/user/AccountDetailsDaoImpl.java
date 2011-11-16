package com.cloud.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria2;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.SearchCriteriaService;

@Local(value={AccountDetailsDao.class})
public class AccountDetailsDaoImpl extends GenericDaoBase<AccountDetailVO, Long> implements AccountDetailsDao {
	protected final SearchBuilder<AccountDetailVO> accountSearch;
	
	protected AccountDetailsDaoImpl() {
		accountSearch = createSearchBuilder();
		accountSearch.and("accountId", accountSearch.entity().getAccountId(), Op.EQ);
		accountSearch.done();
	}
	
	@Override
	public Map<String, String> findDetails(long accountId) {
		SearchCriteriaService<AccountDetailVO, AccountDetailVO> sc = SearchCriteria2.create(AccountDetailVO.class);
		sc.addAnd(sc.getEntity().getAccountId(), Op.EQ, accountId);
		List<AccountDetailVO> results = sc.list();
		Map<String, String> details = new HashMap<String, String>(results.size());
		for (AccountDetailVO r : results) {
			details.put(r.getName(), r.getValue());
		}
		return details;
	}

	@Override
	public void persist(long accountId, Map<String, String> details) {
        Transaction txn = Transaction.currentTxn();
        txn.start();
        SearchCriteria<AccountDetailVO> sc = accountSearch.create();
        sc.setParameters("accountId", accountId);
        expunge(sc);
        for (Map.Entry<String, String> detail : details.entrySet()) {
        	AccountDetailVO vo = new AccountDetailVO(accountId, detail.getKey(), detail.getValue());
        	persist(vo);
        }
        txn.commit();
	}

	@Override
	public AccountDetailVO findDetail(long accountId, String name) {
		SearchCriteriaService<AccountDetailVO, AccountDetailVO> sc = SearchCriteria2.create(AccountDetailVO.class);
		sc.addAnd(sc.getEntity().getAccountId(), Op.EQ, accountId);
		sc.addAnd(sc.getEntity().getName(), Op.EQ, name);
		return sc.find();
	}

	@Override
	public void deleteDetails(long accountId) {
		SearchCriteria<AccountDetailVO> sc = accountSearch.create();
        sc.setParameters("accountId", accountId);
        List<AccountDetailVO> results = search(sc, null);
        for (AccountDetailVO result : results) {
        	remove(result.getId());
        }
	}

	@Override
    public void update(long accountId, Map<String, String> details) {
	    Map<String, String> oldDetails = findDetails(accountId);
	    oldDetails.putAll(details);
	    persist(accountId, oldDetails);
    }
}