package com.fincore.backend.service;

import java.math.BigDecimal;
import java.security.SecureRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.entity.User;
import com.fincore.backend.entity.Wallet;
import com.fincore.backend.enums.WalletStatus;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.repository.WalletRepository;

@Service
public class WalletService {
	private final WalletRepository walletRepository;
	private final UserRepository userRepository;
	private final SecureRandom secureRandom= new SecureRandom();
	
	public WalletService( WalletRepository walletRepository, UserRepository userRepository) {
		this.userRepository=userRepository;
		this.walletRepository=walletRepository;
	}
	
	@Transactional
	public Wallet createWallet(Long userId) {
		User user=  userRepository.findById(userId).orElseThrow(()-> new RuntimeException("User not found"));
		
		if(walletRepository.findByUserId(userId).isPresent()) {
			throw new RuntimeException("User already has a wallet");
		}
		
		Wallet wallet =new Wallet();
		
		wallet.setWalletNumber(generateWalletNumber());
		wallet.setUser(user);
		wallet.setBalance(BigDecimal.ZERO);
		wallet.setCurrency("INR");
		wallet.setStatus(WalletStatus.ACTIVE);
		wallet.setVersion(0L);
		
		return walletRepository.save(wallet);
	}
	
	@Transactional
	public Wallet save(Wallet wallet) {
	    return walletRepository.save(wallet);
	}
	
	@Transactional(readOnly=true)
	public Wallet getWallet(Long walletId) {
		return walletRepository.findById(walletId).orElseThrow(()-> new RuntimeException("Wallet not found"));
		
	}
	
	@Transactional(readOnly=true)
	public Wallet getWalletByUserId(Long userId) {
		return walletRepository.findById(userId).orElseThrow(()-> new RuntimeException("Wallet not found"));
	}
	
	@Transactional(readOnly=true)
	public BigDecimal getBalance(Long walletId) {
		Wallet wallet=getWallet(walletId);
		return wallet.getBalance();
	}
	
	@Transactional
    public void activateWallet(Long walletId) {

        Wallet wallet = walletRepository.findById(walletId).orElseThrow(() ->new RuntimeException("Wallet not found"));

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new RuntimeException(
                    "Closed wallet cannot be activated");
        }

        wallet.setStatus(WalletStatus.ACTIVE);

        walletRepository.save(wallet);
    }
	
	@Transactional
	public void freezeWallet(Long walletId) {
		Wallet wallet = walletRepository.findById(walletId).orElseThrow(()->new RuntimeException("Wallet not found"));
		if(wallet.getStatus()==WalletStatus.CLOSED) {
			throw new RuntimeException("Closed wallet cannot be frozen");
		}
		wallet.setStatus(WalletStatus.FROZEN);
		walletRepository.save(wallet);
	}
	
	@Transactional
	public Wallet getWalletForUpdate(Long walletId) {

        return walletRepository.findByIdForUpdate(walletId).orElseThrow(() ->new RuntimeException("Wallet not found"));
    }

    public void validateActive(Wallet wallet) {

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new RuntimeException(
                    "Wallet is not active");
        }
    }

    public void validateSufficientBalance(
            Wallet wallet,
            BigDecimal amount) {

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException(
                    "Insufficient wallet balance");
        }
    }

    public void debit(
            Wallet wallet,
            BigDecimal amount) {

        validateActive(wallet);
        validateSufficientBalance(wallet, amount);

        wallet.setBalance(
                wallet.getBalance().subtract(amount)
        );
    }

    public void credit(
            Wallet wallet,
            BigDecimal amount) {

        validateActive(wallet);

        wallet.setBalance(
                wallet.getBalance().add(amount)
        );
    }
    
    @Transactional
    public Wallet fundWallet(Long walletId, BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Funding amount must be greater than zero");
        }

        Wallet wallet = walletRepository.findById(walletId).orElseThrow(() -> new RuntimeException("Wallet not found"));

        validateActive(wallet);

        wallet.setBalance(wallet.getBalance().add(amount));

        return walletRepository.save(wallet);
    }
	
	
	
	private String generateWalletNumber() {

        String walletNumber;

        do {
            walletNumber = generateNumber();

        } while (walletRepository
                .existsByWalletNumber(walletNumber));

        return walletNumber;
    }
	private String generateNumber() {

        StringBuilder number = new StringBuilder();

        for (int i = 0; i < 12; i++) {
            number.append(
                    secureRandom.nextInt(10)
            );
        }

        return number.toString();
    }
}
