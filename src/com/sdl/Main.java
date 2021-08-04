package com.sdl;


import java.io.FileWriter;
import java.io.IOException;

public class Main {

     public void run() {
        // Uncomment these 2 lines to enable the testing of the simulation engine
        // against results that were computed by hand for the article
        //compareTwoBlocksAgainstAdvance();
        //System.exit(1);

        export(0.50,10);
        export(0.50,100);
        export(0.50,1000);
        export(0.45,10);
        export(0.45,100);
        export(0.45,1000);
        export(0.40,10);
        export(0.40,100);
        export(0.40,1000);
    }

    // This is for testing
    // Make sure we don't break things
    public void compareTwoBlocksAgainstAdvance() {
        SimArguments args = new SimArguments();
        double q = 0.5;
        int bribeMult = 20;
        boolean checkER = true;
        boolean doPARENT = true;
        boolean doNO = true;
        boolean doCHILD = true;
        // The PRECHILD stategy only will be good if there are many uncles?
        // Depends: if uncles are mined by delay on tempalte update, then
        // miners will not mine uncles of the attackers block containing the bribe,
        // because their previous block is the honest block.
        boolean doPRECHILD = true;

        loadBasicArguments(args,q,bribeMult);
        args.maxSimulations = 1;
        ExpectedResults er = new ExpectedResults();

        if (doPARENT) {
            args.moveBribe = MoveBribe.PARENT;
            er.revenueH = 26.2 / 2; // 50% chances to mine the next block
            er.revenueAA = 15 + 28; // = 43
            er.revenueAH = 0;
            er.revenueAHH = 5;
            er.revenueAHA = 0; // non-final state
            er.revenueAHAA = 43 + 26.2; // = 69.2
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> PARENT");
            computeRecordedEvents(args, er, checkER);
        }
        if (doNO) {
            args.moveBribe = MoveBribe.NO;
            er.revenueH = 26.2 / 2; // 50% chances to mine the next block
            er.revenueAA = 15 + 28;
            er.revenueAH = 0;
            er.revenueAHH = 15; // better
            er.revenueAHA = 0; // non-final state
            er.revenueAHAA = 43 + 26.2; // = 69.2
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> NO");
            computeRecordedEvents(args, er, checkER);
        }
        if (doCHILD) {
            args.moveBribe = MoveBribe.CHILD;
            er.revenueH = 26.2 / 2; // 50% chances to mine the next block
            er.revenueAA = 35; // worse
            er.revenueAH = 0;
            er.revenueAHH = 15; // better
            er.revenueAHA = 0; // non-final state
            er.revenueAHAA = 5 + 30 + 28; // =63
            // Creo que este da mejor porque cuando gano, le hago perder a los demas 10.
            // (porque comparto 5 en lugar de 15). Esos 10 tienen que volver con todas
            // las futuras comisiones del bribe.
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> CHILD");
            computeRecordedEvents(args, er, checkER);
        }

        if (doPRECHILD) {
            args.moveBribe = MoveBribe.PRECHILD;
            args.z = 0;
            args.stopAtDeficit = 2;
            args.minPublishLength =2;
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> PRECHILD");
            er.revenueH = 0; // this does not end the simulation
            er.revenueAH = 0; // non-final
            er.revenueAA = 40; // no sharing of reward
            er.revenueAHH = 0; // non-final
            er.revenueAHHAA = 5+30+28; // = 63;
            er.revenueAHA = 35; // this is a final state because attacker can manipulate difficulty
            er.revenueAHAA = 0; // not tested, post-final.
            computeRecordedEvents(args, er, checkER);
        }

    }
    public void loadBasicArguments(SimArguments args,double q,int bribeMult) {
        args.z = 2; //catchup blocks
        args.q = q;
        args.maxEvents = 100;
        args.stopAtDeficit = args.z+1;
        args.avgBlockFee = 10;
        args.bribeFee =args.avgBlockFee*bribeMult;
        args.recordedEvents = null;
        args.moveBribe = MoveBribe.NO;
        args.uncleShare =  true;
        args.avgUnclesPerBlock  =0;
        args.getHonestRevenueOnly = false;

    }
    public void export(double q,int bribeMult) {
        //printRewards(6,10,200);
        //System.exit(0);
        FeeSnipSimulator sim = new FeeSnipSimulator();
        SimArguments args = new SimArguments();

        loadBasicArguments(args,q,bribeMult);
        try {
            FileWriter myWriter = new FileWriter("simresults-"+q+"-"+bribeMult+"x.csv");
            //myWriter.write("Files in Java might be tricky, but it is fun enough!");
            myWriter.write("Strategy,Revenue\n");

            System.out.println("PreDelay");
            System.out.println("--------------------------------------------------");
            // the advantage is zero.
            args.z=0;
            // If the attacker mines a single block, then we keep trying a second block
            // If the honest miners mines the bribe block, we continue trying.
            // Therefore we stop at deficit 2
            args.stopAtDeficit = 2;
            args.moveBribe =MoveBribe.PRECHILD;
            SimResult resultSim2PreChild = sim.simulateFeeSnip(args,new SimResult());
            myWriter.write("PDB," + resultSim2PreChild.profit_increment_percent+"\n");

            // Honest Mining and SameBlock
            System.out.println("One Selfish Blocks");
            System.out.println("--------------------------------------------------");
            args.z = 1;
            args.stopAtDeficit = args.z+1;
            SimResult resultSim = sim.simulateFeeSnip(args,new SimResult());
            System.out.println("-----------------------------------------------");
            //myWriter.write("Honest," + resultSim.fix_honest_revenue+"\n");
            myWriter.write("OSB," + resultSim.profit_increment_percent+"\n");

            // TwoBlocks
            System.out.println("Two Selfish Blocks");
            System.out.println("--------------------------------------------------");
            args.z=2;
            args.stopAtDeficit = args.z+1;
            SimResult resultSim2No = sim.simulateFeeSnip(args,new SimResult());
            myWriter.write("TSB," + resultSim2No.profit_increment_percent+"\n");

            System.out.println("Delay");
            System.out.println("--------------------------------------------------");
            args.z=2;
            args.stopAtDeficit = args.z+1;
            args.moveBribe =MoveBribe.CHILD;
            SimResult resultSim2Child = sim.simulateFeeSnip(args,new SimResult());
            myWriter.write("DB," + resultSim2Child.profit_increment_percent+"\n");

            System.out.println("Advance");
            System.out.println("--------------------------------------------------");
            args.moveBribe = MoveBribe.PARENT;
            SimResult resultSim2Parent = sim.simulateFeeSnip(args,new SimResult());
            myWriter.write("AB," + resultSim2Parent.profit_increment_percent+"\n");

            System.out.println("-----------------------------------------------");


            // z= 1: attacker must mine a single block
            // z= 2: attacker must mine 2 blocks .. etc
            myWriter.close();
            //computeRecordedEvents();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }



    class ExpectedResults {
        double revenueH;
        double revenueAH; // non-final
        double revenueAA;
        double revenueAHH;
        double revenueAHA; // non-final
        double revenueAHAA;
        double revenueAHHAA; // only possible in PRECHILD

    }

    void testAbort(String s) {
        System.out.println("##################################");
        System.out.println(s);
        System.out.println("##################################");
        System.exit(1);
    }

    void computeRecordedEvents(SimArguments args,ExpectedResults er,boolean checkER) {
        FeeSnipSimulator sim = new FeeSnipSimulator();
        args.maxSimulations = 1;

        //////////////////////////////////////////////////////////////

        double prob = 0;
        boolean doH = true;
        boolean doAH = true;
        boolean doAA = true;
        boolean doAHH = true;
        boolean doAHA = true;
        boolean doAHAA = true;

        //doAHHAA: this case only matters if attacker cannot modify difficulty
        // if he can, he'll win at AHHA
        boolean doAHHAA = false;


        //////////////////////////////////////////////////////////////
        if (doH) {
            // In al strategies but PRECHILD, a single honest block stops
            if (args.moveBribe==MoveBribe.PRECHILD) {
                // A single H block does not finish reward
                args.maxEvents = 1;
            } else
            // We finish at first event, and add the average reward for second event
            args.maxEvents = 2;

            args.recordedEvents = new boolean[]{false}; // 1H ends it.
            SimResult resultH = sim.simulateFeeSnip(args, new SimResult());
            if ((er != null) && (checkER) && (resultH.avg_selfish_revenue != er.revenueH)) {
                testAbort("revenueH mismatch");
            }
            prob += resultH.eventProb;
        }

        //////////////////////////////////////////////////////////////
        if (doAH) {
            args.maxEvents = 2;
            args.recordedEvents = new boolean[]{true, false};
            SimResult resultAH = sim.simulateFeeSnip(args, new SimResult());

            if ((er != null) && (checkER) && (resultAH.avg_selfish_revenue != er.revenueAH)) {
                testAbort("revenueAH mismatch");
            }
            prob += resultAH.eventProb;
        }
        //////////////////////////////////////////////////////////////
        if (doAA) {
            args.maxEvents = 2;
            args.recordedEvents = new boolean[]{true, true};
            SimResult resultAA = sim.simulateFeeSnip(args, new SimResult());
            if ((er != null) && (checkER) && (resultAA.avg_selfish_revenue != er.revenueAA)) {
                testAbort("revenueAA mismatch");
            }
            prob += resultAA.eventProb;
        }
        //////////////////////////////////////////////////////////////
        if (doAHH) {
            args.maxEvents = 3;
            args.recordedEvents = new boolean[]{true, false, false};
            SimResult resultAHH = sim.simulateFeeSnip(args, new SimResult());
            if ((er != null) && (checkER) && (resultAHH.avg_selfish_revenue != er.revenueAHH)) {
                testAbort("revenueAHH mismatch");
            }
        }
        if (doAHA) {
            args.maxEvents = 3;
            args.recordedEvents = new boolean[]{true, false, true};
            SimResult resultAHA = sim.simulateFeeSnip(args, new SimResult());
            if ((er != null) && (checkER) && (resultAHA.avg_selfish_revenue != er.revenueAHA)) {
                testAbort("revenueAHA mismatch");
            }
        }
        if (args.moveBribe!=MoveBribe.PRECHILD) {
            if (doAHAA) {
                args.maxEvents = 4;
                args.recordedEvents = new boolean[]{true, false, true, true};
                SimResult resultAHAA = sim.simulateFeeSnip(args, new SimResult());
                if ((er != null) && (checkER) && (resultAHAA.avg_selfish_revenue != er.revenueAHAA)) {
                    testAbort("revenueAHAA mismatch");
                }
            }
        }


        if (args.moveBribe==MoveBribe.PRECHILD) {
            if (doAHHAA) {
                args.maxEvents = 5;
                args.recordedEvents = new boolean[]{true, false, false, true, true};
                SimResult resultAHHAA = sim.simulateFeeSnip(args, new SimResult());
                if ((er != null) && (checkER) && (resultAHHAA.avg_selfish_revenue != er.revenueAHHAA)) {
                    testAbort("revenueAHHAA mismatch");
                }
            }
        }
        //////////////////////////////////////////////////////////////////////////
        // This is testing code to check that probabilities add-up correctly
        // It's currently deactivated
        /*
        System.out.println("Sum of probabilities: "+prob);
        double weigthed =
                resultAA.avg_selfish_revenue*resultAA.eventProb+
                resultAH.avg_selfish_revenue*resultAH.eventProb+
                resultH.avg_selfish_revenue*resultH.eventProb;
        System.out.println("Weigthed revenue: "+weigthed);
        */
        //////////////////////////////////////////////////////////////////////////
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
