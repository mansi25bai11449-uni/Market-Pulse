package com.marketpulse.model;

public enum TimeInForce {
    GTC, // Good-Till-Cancel (Default)
    IOC, // Immediate-Or-Cancel: match what is possible immediately, cancel the rest
    FOK  // Fill-Or-Kill: must fill full quantity immediately or reject/cancel entirely
}
