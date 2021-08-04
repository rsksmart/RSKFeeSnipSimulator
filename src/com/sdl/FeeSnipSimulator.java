package com.sdl;

import java.util.Random;

public class FeeSnipSimulator {
    public SimPartialResults spr = new SimPartialResults();
    public Random rnd = new Random(); // random seed

    // This is https://en.wikipedia.org/wiki/Exponential_distribution
    // Note L == 1 / lambda
    // The number of events per time unit is a Poisson distribution,
    // but the time between events is an exponential distribution
    public double poissonRandomInterarrivalDelay(double L) {
        return (Math.log(1.0-rnd.nextDouble())/-L);
    }

    // This is the exponential cumulative function. The probability
    // that a block arrives in less than t time
    // L is lambda (the rate)
    public double  blockArrivesBefore(double L, double t) {
        return (1.0-Math.exp(-L*t));
    }

    public double  blockArrivesAfter(double L, double t) {
        return (Math.exp(-L*t));
    }



    double getFutureRevenue(double q,int eventsLeft,
                            double avg_block_fee_revenue,
                            double honest_pot) {


        double r =  0;
        // If we're asked to give the future revenue for many events, assume infinite following blocks
        if (eventsLeft>=10) {
            r = q * eventsLeft * avg_block_fee_revenue;
            // q*honest_pot is future revenue for infinite following blocks
            r =r + q * honest_pot;
            return r;
        }
        // If we're asked for few events, we assume the caller wants a limited depth analysis.
        while (eventsLeft>0) {
            double paid = honest_pot /10;
            r +=paid+avg_block_fee_revenue;
            honest_pot = honest_pot-paid;
            eventsLeft--;
        }
        return r*q;
    }

    public void printRewards(int z,double avgBlockFee,double bribeFee) {
        double initial_honest_pot = bribeFee+avgBlockFee*9;
        for (int w=0;w<z;w++) {
            initial_honest_pot +=avgBlockFee;
            double paid = initial_honest_pot /10;
            initial_honest_pot = initial_honest_pot-paid;
            System.out.println("Paid: "+paid);
        }
    }
    //simulateFeeSnip:
    // If recordedEvents is non-null, it will play the events and continue with random events afterward
    // If recordedEvents is null, it will only chose random outcomes
    //
    public SimResult simulateFeeSnip( SimArguments args,SimResult result) {

        preparePartialResults(args,spr);

        // We're going to analyze what happens over a period of time, measured
        // in mining events. The period of time is maxBlocks events.
        result.fix_honest_revenue = args.q * (spr.initial_honest_pot +
                args.maxEvents * spr.avg_block_fee_revenue);

        if (args.getHonestRevenueOnly)
            return result;


        printRecordedEvents(args.recordedEvents);
        printArgs(args);

        SimState ss = new SimState(); // re-use state

        for (int s=0;s<args.maxSimulations;s++) {
            prepareSimulation(args, spr, ss);
            result.attackerGaveUp = false;

            while (ss.i < args.maxEvents) {
                ss.i++; // count the number of events processed
                boolean attackerMines = chooseMiner(args, spr, ss);

                if (attackerMines)
                    attackerMinesBlock(args, spr, ss);
                else
                    honestMinerMinesBlock(args, spr, ss);

                if (ss.honestBlocks >= ss.attackersBlocks + args.stopAtDeficit) {
                    honestMinerWins(args, spr, ss);
                    result.attackerGaveUp = true;
                    break; // no need to continue, catchup probability is too low
                }
                boolean attackerWinsRace = decideIfAttackerWins(args, spr, ss);
                if (attackerWinsRace) {
                    attackerWins(args, spr, ss);
                    break;
                }
            }
            spr.eventsProcessed = ss.i;
            spr.acumNumberOfEvents += ss.i;
            if (ss.i >= 6) {
                // 3 minutes
                spr.timesContentionTook3Minutes++;
             }
            if (!ss.stop) {
                spr.nonStopped++;
                if (args.recordedEvents!=null)
                  System.out.println("WARNING: not a stop state");
                // here we cannot follow until the end
                //result.attackerGaveUp = true;
            }
        } // for each simulation

        computeResults(args,spr,result);
        printResults(args,spr,result);
        return result;
    }

    public void computeResults(SimArguments args,SimPartialResults spr,SimResult result) {
        result.avg_selfish_revenue = spr.acum_selfish_revenue / args.maxSimulations;
        if (args.recordedEvents == null) {
            spr.attackerWinsLen = spr.acumAttackerWinsLen * 1.0 / spr.attackerWins;
            spr.honestWinsLen = spr.acumHonestWinsLen* 1.0 / spr.honestWins;
            spr.contentionLen = (1.0*spr.acumAttackerWinsLen+spr.acumHonestWinsLen)/
                    (spr.attackerWins+spr.honestWins);
            spr.averageEvents = (1.0*spr.acumNumberOfEvents/args.maxSimulations);
            spr.contentionTook3MinutesPercentile = 100-(100.0*spr.timesContentionTook3Minutes/args.maxSimulations);
        }
        result.net_revenue = result.avg_selfish_revenue - result.fix_honest_revenue;
        // Profit
        result.profit_increment_percent = result.net_revenue * 100 / result.fix_honest_revenue;
        if (args.recordedEvents != null) {
            result.eventProb = spr.prob;
        }
        result.successProb = spr.attackerWins * 1.0 / args.maxSimulations;
    }

    public void preparePartialResults(SimArguments args,SimPartialResults spr) {
        spr.attackerWins = 0;
        spr.honestWins = 0;
        spr.acumAttackerWinsLen = 0;
        spr.acumHonestWinsLen = 0;
        spr.attackerWinsLen = 0;
        spr.honestWinsLen = 0;
        spr.contentionLen = 0;
        spr.acum_selfish_revenue = 0;
        spr.initial_pot = args.bribeFee;
        spr.avg_block_fee_revenue = args.avgBlockFee;

        spr.nonStopped = 0;
        // First mine the z blocks on the honest chain, reducing the bribe
        spr.initial_honest_pot = spr.initial_pot;

        spr.uncleFactor = 1.0 / (1 + args.avgUnclesPerBlock);
        spr.selfishUncleFactor = 1.0 / (2 + args.avgUnclesPerBlock);


        // If the initial deficit is z, this means the honst chain has only
        // mined (z-1) blocks from the bribe
        // We don't compute the uncle share at this point because we don't
        // know if the attacker will be able to mine an uncle.
        spr.firstBlockHonestReward = 0;
        spr.honestChainDistToBribe = 1;

        // If we're in pre-child strategy, the first block mined by the honest chain
        // is the one containing the bribe, so there is nothing to discount.
        if (args.moveBribe == MoveBribe.PRECHILD) {
            double paid = spr.initial_honest_pot / 10;
            spr.firstBlockHonestReward = paid + spr.avg_block_fee_revenue;// This should never be used in this mode
            spr.honestChainDistToBribe = 0;
        } else
            for (int w = 0; w < spr.honestChainDistToBribe; w++) {
                // we do not consider here uncles because we count
                // the fees paid to all miners (not to the one of the current block)
                double paid = spr.initial_honest_pot / 10;
                if (w == 0)
                    spr.firstBlockHonestReward = paid + spr.avg_block_fee_revenue;
                spr.initial_honest_pot = spr.initial_honest_pot - paid;
            }

        // In this case, the first block mined by the attacker does not share the bribe,
        // but shares a normal block's reward
        if (args.moveBribe == MoveBribe.PARENT) {
            spr.firstBlockHonestReward = spr.avg_block_fee_revenue;
        }

        spr.eventsProcessed =0;
        spr.censorship = 0;
        spr.prob = 1;
        spr.acumNumberOfEvents =0;
        spr.timesContentionTook3Minutes =0;
    }

    public void prepareSimulation(SimArguments args,SimPartialResults spr,SimState ss) {
        ss.attackersBlocks = 0;
        ss.honestBlocks = args.z; // the number of blocks to mine
        ss.selfish_revenue = 0;
        ss.selfish_revenue_share = 0;
        ss.honest_revenue = 0;
        // This pots do not count the avg_block_fee_revenue
        if ((args.moveBribe != MoveBribe.CHILD) && (args.moveBribe != MoveBribe.PRECHILD))
            ss.selfish_pot = spr.initial_pot;
        else
            ss.selfish_pot = 0;
        ss.honest_pot = spr.initial_honest_pot;
        ss.stop = false;
        ss.i = 0;
    }

    public boolean decideIfAttackerWins(SimArguments args,SimPartialResults spr,SimState ss) {
        boolean attackerWinsRace = false;
        if (args.moveBribe == MoveBribe.PRECHILD) {
            // We only stop if the attacker mined more than one block.
            if (ss.attackersBlocks >= args.minPublishLength)
                attackerWinsRace = (ss.attackersBlocks >= ss.honestBlocks);
        } else
            attackerWinsRace = (ss.attackersBlocks >= ss.honestBlocks);
        return attackerWinsRace;
    }

    public void attackerWins(SimArguments args,SimPartialResults spr,SimState ss) {

        spr.acumAttackerWinsLen += ss.attackersBlocks;
        spr.attackerWins++;
        int eventsLeft = args.maxEvents - ss.i;
        // Now add the revenue for behaving correctly for the remaining
        // blocks
        double future_revenue =
                getFutureRevenue(args.q, eventsLeft, spr.avg_block_fee_revenue, ss.selfish_pot);

        double revenue = ss.selfish_revenue + future_revenue;
        // If the attacker can't censor revenue share, we assume the uncle
        // block will be referenced
        if (ss.attackersBlocks < 10) {
            if (ss.honestBlocks > 0)
                revenue -= ss.selfish_revenue_share;
        } else {
            // successfull censorship
            spr.censorship++;
        }
        spr.acum_selfish_revenue += revenue;
        ss.stop = true;
    }

    public void honestMinerWins(SimArguments args,SimPartialResults spr,SimState ss) {
        // Discount the initial z blocks added to get the actual numnber of blocks mined
        // since  simulation start.
        spr.acumHonestWinsLen += (ss.honestBlocks-args.z);
        spr.honestWins++;
        if (args.uncleShare) {
            if (ss.attackersBlocks > 0) {
                // If the attacker was unable to mine any block,
                // then the honest miners take all.
                // If the miner was able to mine a block, then we give half
                // of the reward to him now
                spr.acum_selfish_revenue += spr.firstBlockHonestReward * spr.selfishUncleFactor;
                // Here we assume that the attacker is always able to publish his own
                // block so the honest miners can reference it.
            }
        }
        int eventsLeft = args.maxEvents - ss.i;
        double future_revenue =
                getFutureRevenue(args.q, eventsLeft, spr.avg_block_fee_revenue, ss.honest_pot);
        spr.acum_selfish_revenue += future_revenue;
        ss.stop = true;
    }

    public boolean chooseMiner(SimArguments args,SimPartialResults spr,SimState ss) {
        boolean attackerMines =false;
        // Start with the recorded events
        if ((args.recordedEvents != null) && (ss.i <= args.recordedEvents.length)) {
            attackerMines = args.recordedEvents[ss.i - 1];
            if (attackerMines)
                spr.prob *= args.q;
            else
                spr.prob *= (1 - args.q);
        } else
            attackerMines = rnd.nextDouble() < args.q;
        return attackerMines;
    }

    public void honestMinerMinesBlock(SimArguments args, SimPartialResults spr, SimState ss) {
        ss.honestBlocks++;
        double paid = ss.honest_pot / 10;
        ss.honest_revenue += paid + spr.avg_block_fee_revenue;
        ss.honest_pot = ss.honest_pot - paid;
    }

    public void attackerMinesBlock(SimArguments args, SimPartialResults spr, SimState ss) {
        ss.attackersBlocks++;

        if ((args.moveBribe==MoveBribe.CHILD) || (args.moveBribe==MoveBribe.PRECHILD)) {
            if (ss.attackersBlocks==2) {
                ss.selfish_pot += spr.initial_pot;
            }
        }

        double removedFromSelfishPot = ss.selfish_pot /10;
        double paidToMiner = spr.avg_block_fee_revenue+removedFromSelfishPot;

        if ((args.uncleShare) && (ss.attackersBlocks==1)) {
            ss.selfish_revenue_share +=paidToMiner*spr.selfishUncleFactor;
        }
        ss.selfish_revenue +=paidToMiner;
        ss.selfish_pot = ss.selfish_pot-removedFromSelfishPot;
    }

    public void printResults(SimArguments args,SimPartialResults spr,SimResult result) {
        if ((args.recordedEvents != null) && (args.maxSimulations == 1)) {
            if (args.recordedEvents.length != spr.eventsProcessed) {
                System.out.println("Events mismatch, probability will be wong");
                System.exit(1);
            }
        }
        if (args.printExtraResuls) {
            System.out.println("censorship: " + spr.censorship);
            System.out.println("censorship[%]: " + spr.censorship * 100 / args.maxSimulations);
            System.out.println("nonStopped: " + spr.nonStopped);
        }


        if (args.recordedEvents == null) {
            System.out.println("Average attacker blocks to catchup: " + spr.attackerWinsLen);
            System.out.println("Average honest blocks to win: " + spr.honestWinsLen);
            System.out.println("Average contention length: "+spr.contentionLen);
            System.out.println("Average number of events: "+spr.averageEvents);
            System.out.println("Percentile contention took less than 3 minutes: "+spr.contentionTook3MinutesPercentile);
            double avg_selfish_when_win = spr.acum_selfish_revenue / spr.attackerWins;
            System.out.println("Average attack revenue when winning: " + avg_selfish_when_win);
        } else
            System.out.println("Prob: " + spr.prob);

        System.out.println("Average attack revenue: " + result.avg_selfish_revenue);
        System.out.println("Average honest revenue: " + result.fix_honest_revenue);
        System.out.println("Net revenue: " + result.net_revenue);
        System.out.println("Profit increment [%]: "+result.profit_increment_percent);
    }

    public  void printArgs(SimArguments args) {
        if (args.dumpArgs) {
            System.out.println("q=" + args.q);
            System.out.println("z=" + args.z);
            System.out.println("maxEvents=" + args.maxEvents);
            System.out.println("stopAtDeficit=" + args.stopAtDeficit);
            System.out.println("avgBlockFee=" + args.avgBlockFee);
            System.out.println("bribeFee=" + args.bribeFee);
            System.out.println("moveBribe=" + args.moveBribe);
            System.out.println("uncleShare=" + args.uncleShare);
        } else
            System.out.println("moveBribe=" + args.moveBribe);
    }

    public  void printRecordedEvents(boolean [] recordedEvents) {
        if (recordedEvents != null) {
            String S = "";
            for (int h = 0; h < recordedEvents.length; h++) {
                if (recordedEvents[h])
                    S = S + "A";
                else
                    S = S + "H";
            }
            System.out.println("-------------------------------");
            System.out.println("Case: [" + S + "]");
        }
    }

    public double AttackerSuccessProbability(double q, int z,int stopAtDeficit,
                                             boolean[] recordedEvents) {

        int max = 1000;
        int maxSimulations = 100000;
        int maxEvents = 2;
        int attackerWins  =0;
        int acumCatchupLen =0;
        double catchupLen =0;
        int nonStopped =0;

        if (recordedEvents!=null)
            maxSimulations = 1;

        double prob = 1;
        if (recordedEvents!=null) {
            String S ="";
            for(int h=0;h<recordedEvents.length;h++) {
                if (recordedEvents[h])
                    S = S + "A";
                else
                    S = S + "H";
            }
            System.out.println("-------------------------------");
            System.out.println("Case :"+S);
        }
        //result.attackerGaveUp = false;

        for (int s=0;s<maxSimulations;s++) {
            int attackersBlocks=0;
            int honestBlocks = z; // the number of blocks to mine
            boolean stop = false;
            // Change this: extend until honest chain reaches  x blocks
            int i =0;
            while (i<maxEvents) {
                //for (int i = 0; i < maxBlocks; i++) {
                boolean attackerMines = false;
                if (recordedEvents!=null) {
                    attackerMines = recordedEvents[i];
                    if (attackerMines)
                        prob *=q;
                    else
                        prob *=(1-q);
                } else
                    attackerMines =rnd.nextDouble() < q;

                if (attackerMines) {
                    attackersBlocks++;
                } else {
                    honestBlocks++;
                }

                if (honestBlocks >=attackersBlocks+stopAtDeficit) {
                    stop = true;
                    //result.attackerGaveUp = true;
                    break; // no need to continue, catchup probability is too low
                }
                if (attackersBlocks >= honestBlocks) {
                    acumCatchupLen +=attackersBlocks;
                    attackerWins++;
                    //result.attackerGaveUp = false;
                    stop =true;
                    break;
                }
                i++;
            }
            if (!stop) {
                nonStopped++;
                //result.attackerGaveUp = true;
            }

        }

        System.out.println("nonStopped: "+nonStopped);

        if (recordedEvents==null) {
            catchupLen = acumCatchupLen * 1.0 / attackerWins;
            System.out.println("Average catchup blocks: " + catchupLen);
        } else
            System.out.println("Prob: "+prob);

        return attackerWins*1.0/maxSimulations;
    }
}
